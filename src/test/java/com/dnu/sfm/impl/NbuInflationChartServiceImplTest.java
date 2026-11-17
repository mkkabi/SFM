package com.dnu.sfm.service.impl;

import com.dnu.sfm.dto.NbuAnnualInflationPointDto;
import com.dnu.sfm.dto.NbuInflationScatterSeriesDto;
import com.dnu.sfm.model.NbuYearlyInflation;
import com.dnu.sfm.service.NbuYearlyInflationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Year;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the DB-first / fetch-on-missing orchestration of the annual
 * inflation scatter chart. The NBU yearly service is simulated with a mutable
 * "database": a year becomes headline-visible only after upsertYearlyRecords
 * is called for it.
 */
@ExtendWith(MockitoExtension.class)
class NbuInflationChartServiceImplTest {

    @Mock
    private NbuYearlyInflationService yearlyInflationService;

    @InjectMocks
    private NbuInflationChartServiceImpl service;

    /** Years currently stored in the simulated database (headline rows). */
    private final Set<Integer> storedYears = new HashSet<>();

    /** Year -> headline annual CPI rate visible through findHeadlineAnnualCpi. */
    private final Map<Integer, Double> rateByYear = new HashMap<>();

    private void seedDatabase(int year, double rate) {
        storedYears.add(year);
        rateByYear.put(year, rate);
    }

    /** Simulates the DB-backed headline lookup of the yearly service. */
    private void stubHeadlineLookup() {
        when(yearlyInflationService.findHeadlineAnnualCpi(anyInt())).thenAnswer(inv -> {
            int year = inv.getArgument(0);
            return storedYears.contains(year)
                    ? Optional.of(headline(year))
                    : Optional.empty();
        });
    }

    /**
     * Simulates the remote fetch + transactional persist: fetching returns a
     * non-empty payload, and persisting makes the headline row visible.
     */
    private void stubFetchAndPersist() {
        when(yearlyInflationService.fetchFromNbu(anyInt())).thenAnswer(inv ->
                List.of(new NbuYearlyInflation(inv.getArgument(0),
                        new BigDecimal("10.0"), "DTPY_", "Total", "Annual change")));
        doAnswer(inv -> {
            List<NbuYearlyInflation> entities = inv.getArgument(0);
            entities.stream().mapToInt(NbuYearlyInflation::getInflationYear).forEach(year -> {
                storedYears.add(year);
                rateByYear.putIfAbsent(year, 10.0);
            });
            return entities.size();
        }).when(yearlyInflationService).upsertYearlyRecords(any());
    }

    private NbuYearlyInflation headline(int year) {
        return new NbuYearlyInflation(year,
                BigDecimal.valueOf(rateByYear.get(year)), "DTPY_", "Total", "Annual change");
    }

    /** The chart window is always the N fully completed years before the running one. */
    private List<Integer> expectedWindow(int n) {
        int currentYear = Year.now().getValue();
        return IntStream.rangeClosed(currentYear - n, currentYear - 1)
                .boxed()
                .toList();
    }

    @Test
    @DisplayName("All years already stored: everything is served from the DB and the NBU API is never called")
    void allYearsStoredServesFromDatabaseWithoutApiCalls() {
        stubHeadlineLookup();
        List<Integer> window = expectedWindow(5);
        window.forEach(year -> seedDatabase(year, 5.0 + (year % 10)));

        NbuInflationScatterSeriesDto series = service.getAnnualInflationScatterData(5);

        assertThat(series.requestedYears()).isEqualTo(5);
        assertThat(series.fromYear()).isEqualTo(window.get(0));
        assertThat(series.toYear()).isEqualTo(window.get(window.size() - 1));
        assertThat(series.points()).extracting(NbuAnnualInflationPointDto::year)
                .containsExactlyElementsOf(window); // ascending order
        assertThat(series.points())
                .allSatisfy(p -> assertThat(p.source()).isEqualTo(NbuAnnualInflationPointDto.SOURCE_DATABASE));
        assertThat(series.servedFromDatabaseYears()).containsExactlyElementsOf(window);
        assertThat(series.fetchedFromApiYears()).isEmpty();
        assertThat(series.unavailableYears()).isEmpty();

        verify(yearlyInflationService, never()).fetchFromNbu(anyInt());
        verify(yearlyInflationService, never()).upsertYearlyRecords(any());
    }

    @Test
    @DisplayName("Missing years are fetched from the NBU API, stored, and labeled as API-sourced points")
    void missingYearsAreFetchedFromApiAndStored() {
        stubHeadlineLookup();
        stubFetchAndPersist();
        List<Integer> window = expectedWindow(5);
        // Only the two most recent years are already cached
        List<Integer> cached = window.subList(window.size() - 2, window.size());
        List<Integer> missing = window.subList(0, window.size() - 2);
        cached.forEach(year -> seedDatabase(year, 12.5));
        missing.forEach(year -> rateByYear.put(year, 10.0)); // value exposed by the simulated fetch

        NbuInflationScatterSeriesDto series = service.getAnnualInflationScatterData(5);

        assertThat(series.points()).extracting(NbuAnnualInflationPointDto::year)
                .containsExactlyElementsOf(window);
        assertThat(series.points())
                .filteredOn(p -> p.source().equals(NbuAnnualInflationPointDto.SOURCE_DATABASE))
                .extracting(NbuAnnualInflationPointDto::year)
                .containsExactlyElementsOf(cached);
        assertThat(series.points())
                .filteredOn(p -> p.source().equals(NbuAnnualInflationPointDto.SOURCE_API))
                .extracting(NbuAnnualInflationPointDto::year)
                .containsExactlyElementsOf(missing);
        assertThat(series.servedFromDatabaseYears()).containsExactlyElementsOf(cached);
        assertThat(series.fetchedFromApiYears()).containsExactlyElementsOf(missing);
        assertThat(series.unavailableYears()).isEmpty();

        // One remote call per missing year, none for cached years
        for (int year : missing) {
            verify(yearlyInflationService).fetchFromNbu(year);
        }
        for (int year : cached) {
            verify(yearlyInflationService, never()).fetchFromNbu(year);
        }
        // Fetched points carry the rate persisted during this request
        assertThat(series.points())
                .filteredOn(p -> p.source().equals(NbuAnnualInflationPointDto.SOURCE_API))
                .allSatisfy(p -> assertThat(p.ratePercent()).isEqualTo(10.0));
    }

    @Test
    @DisplayName("A year the NBU API has no data for is reported unavailable and simply left off the chart")
    void emptyApiResponseMarksYearUnavailable() {
        stubHeadlineLookup();
        List<Integer> window = expectedWindow(2);
        int brokenYear = window.get(0);
        int goodYear = window.get(1);
        rateByYear.put(goodYear, 5.1);

        // Simulated API: empty payload for the broken year, real data for the good one
        when(yearlyInflationService.fetchFromNbu(brokenYear)).thenReturn(List.of());
        when(yearlyInflationService.fetchFromNbu(goodYear)).thenReturn(
                List.of(new NbuYearlyInflation(goodYear, new BigDecimal("5.1"), "DTPY_", "Total", "Annual change")));
        doAnswer(inv -> {
            List<NbuYearlyInflation> entities = inv.getArgument(0);
            entities.stream().mapToInt(NbuYearlyInflation::getInflationYear).forEach(storedYears::add);
            return entities.size();
        }).when(yearlyInflationService).upsertYearlyRecords(any());

        NbuInflationScatterSeriesDto series = service.getAnnualInflationScatterData(2);

        assertThat(series.unavailableYears()).containsExactly(brokenYear);
        assertThat(series.fetchedFromApiYears()).containsExactly(goodYear);
        assertThat(series.points()).extracting(NbuAnnualInflationPointDto::year)
                .containsExactly(goodYear);
        assertThat(series.points().get(0).source())
                .isEqualTo(NbuAnnualInflationPointDto.SOURCE_API);
        assertThat(series.points().get(0).ratePercent()).isEqualTo(5.1);
        // Exactly one successful persist (the good year): an empty API answer never reaches the upsert
        verify(yearlyInflationService, times(1)).upsertYearlyRecords(any());
    }

    @Test
    @DisplayName("Ten-year window covers exactly the last ten completed years and excludes the running year")
    void tenYearWindowCoversCompletedYearsOnly() {
        stubHeadlineLookup();
        List<Integer> window = expectedWindow(10);
        window.forEach(year -> seedDatabase(year, 8.0));

        NbuInflationScatterSeriesDto series = service.getAnnualInflationScatterData(10);

        assertThat(series.requestedYears()).isEqualTo(10);
        assertThat(series.points()).hasSize(10);
        assertThat(series.fromYear()).isEqualTo(Year.now().getValue() - 10);
        assertThat(series.toYear()).isEqualTo(Year.now().getValue() - 1);
        assertThat(series.points()).extracting(NbuAnnualInflationPointDto::year)
                .doesNotContain(Year.now().getValue());
    }
}
