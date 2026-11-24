package com.dnu.sfm.impl;

import com.dnu.sfm.dto.AnnualInflationPointDto;
import com.dnu.sfm.dto.AnnualInflationScatterSeriesDto;
import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.StatBureauYearlyInflation;
import com.dnu.sfm.service.StatBureauYearlyInflationService;
import com.dnu.sfm.service.impl.StatBureauInflationChartServiceImpl;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the DB-first / fetch-on-missing orchestration of the StatBureau
 * annual inflation scatter chart (USD only). The StatBureau yearly service is
 * simulated with a mutable "database": a year becomes headline-visible only
 * after upsertYearlyRecords is called for it.
 */
@ExtendWith(MockitoExtension.class)
class StatBureauInflationChartServiceImplTest {

    @Mock
    private StatBureauYearlyInflationService yearlyInflationService;

    @InjectMocks
    private StatBureauInflationChartServiceImpl service;

    /** Years currently stored in the simulated database (USD rows). */
    private final Set<Integer> storedYears = new HashSet<>();

    /** Year -> annual CPI rate visible through findHeadlineAnnualCpi(USD, year). */
    private final Map<Integer, Double> rateByYear = new HashMap<>();

    private void seedDatabase(int year, double rate) {
        storedYears.add(year);
        rateByYear.put(year, rate);
    }

    /** Simulates the DB-backed headline lookup of the yearly service. */
    private void stubHeadlineLookup() {
        when(yearlyInflationService.findHeadlineAnnualCpi(eq(Currency.USD), anyInt())).thenAnswer(inv -> {
            int year = inv.getArgument(1);
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
        when(yearlyInflationService.fetchFromStatBureau(eq(Currency.USD), anyInt())).thenAnswer(inv ->
                List.of(new StatBureauYearlyInflation(inv.getArgument(1), Currency.USD,
                        BigDecimal.valueOf(rateByYear.getOrDefault((Integer) inv.getArgument(1), 10.0)))));
        doAnswer(inv -> {
            List<StatBureauYearlyInflation> entities = inv.getArgument(0);
            entities.stream().mapToInt(StatBureauYearlyInflation::getInflationYear).forEach(year -> {
                storedYears.add(year);
                rateByYear.putIfAbsent(year, 10.0);
            });
            return entities.size();
        }).when(yearlyInflationService).upsertYearlyRecords(any());
    }

    private StatBureauYearlyInflation headline(int year) {
        return new StatBureauYearlyInflation(year, Currency.USD,
                BigDecimal.valueOf(rateByYear.get(year)));
    }

    /** The chart window is always the N fully completed years before the running one. */
    private List<Integer> expectedWindow(int n) {
        int currentYear = Year.now().getValue();
        return IntStream.rangeClosed(currentYear - n, currentYear - 1)
                .boxed()
                .toList();
    }

    @Test
    @DisplayName("All years already stored: everything is served from the DB and the StatBureau API is never called")
    void allYearsStoredServesFromDatabaseWithoutApiCalls() {
        stubHeadlineLookup();
        List<Integer> window = expectedWindow(5);
        window.forEach(year -> seedDatabase(year, 2.0 + (year % 10)));

        AnnualInflationScatterSeriesDto series = service.getAnnualInflationScatterData(5);

        assertThat(series.currency()).isEqualTo("USD");
        assertThat(series.requestedYears()).isEqualTo(5);
        assertThat(series.fromYear()).isEqualTo(window.get(0));
        assertThat(series.toYear()).isEqualTo(window.get(window.size() - 1));
        assertThat(series.points()).extracting(AnnualInflationPointDto::year)
                .containsExactlyElementsOf(window); // ascending order
        assertThat(series.points())
                .allSatisfy(p -> assertThat(p.source()).isEqualTo(AnnualInflationPointDto.SOURCE_DATABASE));
        assertThat(series.servedFromDatabaseYears()).containsExactlyElementsOf(window);
        assertThat(series.fetchedFromApiYears()).isEmpty();
        assertThat(series.unavailableYears()).isEmpty();

        verify(yearlyInflationService, never()).fetchFromStatBureau(any(Currency.class), anyInt());
        verify(yearlyInflationService, never()).upsertYearlyRecords(any());
    }

    @Test
    @DisplayName("Missing years are fetched from the StatBureau API, stored, and labeled as API-sourced points")
    void missingYearsAreFetchedFromApiAndStored() {
        stubHeadlineLookup();
        stubFetchAndPersist();
        List<Integer> window = expectedWindow(5);
        // Only the two most recent years are already cached
        List<Integer> cached = window.subList(window.size() - 2, window.size());
        List<Integer> missing = window.subList(0, window.size() - 2);
        cached.forEach(year -> seedDatabase(year, 4.1));
        missing.forEach(year -> rateByYear.put(year, 3.3)); // value exposed by the simulated fetch

        AnnualInflationScatterSeriesDto series = service.getAnnualInflationScatterData(5);

        assertThat(series.currency()).isEqualTo("USD");
        assertThat(series.points()).extracting(AnnualInflationPointDto::year)
                .containsExactlyElementsOf(window);
        assertThat(series.points())
                .filteredOn(p -> p.source().equals(AnnualInflationPointDto.SOURCE_DATABASE))
                .extracting(AnnualInflationPointDto::year)
                .containsExactlyElementsOf(cached);
        assertThat(series.points())
                .filteredOn(p -> p.source().equals(AnnualInflationPointDto.SOURCE_API))
                .extracting(AnnualInflationPointDto::year)
                .containsExactlyElementsOf(missing);
        assertThat(series.servedFromDatabaseYears()).containsExactlyElementsOf(cached);
        assertThat(series.fetchedFromApiYears()).containsExactlyElementsOf(missing);
        assertThat(series.unavailableYears()).isEmpty();

        // One remote call per missing year, none for cached years
        for (int year : missing) {
            verify(yearlyInflationService).fetchFromStatBureau(Currency.USD, year);
        }
        for (int year : cached) {
            verify(yearlyInflationService, never()).fetchFromStatBureau(Currency.USD, year);
        }
        // Fetched points carry the rate persisted during this request
        assertThat(series.points())
                .filteredOn(p -> p.source().equals(AnnualInflationPointDto.SOURCE_API))
                .allSatisfy(p -> assertThat(p.ratePercent()).isEqualTo(3.3));
    }

    @Test
    @DisplayName("A year the StatBureau API has no data for is reported unavailable and simply left off the chart")
    void emptyApiResponseMarksYearUnavailable() {
        stubHeadlineLookup();
        List<Integer> window = expectedWindow(2);
        int brokenYear = window.get(0);
        int goodYear = window.get(1);
        rateByYear.put(goodYear, 2.9);

        // Simulated API: empty payload for the broken year, real data for the good one
        when(yearlyInflationService.fetchFromStatBureau(Currency.USD, brokenYear)).thenReturn(List.of());
        when(yearlyInflationService.fetchFromStatBureau(Currency.USD, goodYear)).thenReturn(
                List.of(new StatBureauYearlyInflation(goodYear, Currency.USD, new BigDecimal("2.9"))));
        doAnswer(inv -> {
            List<StatBureauYearlyInflation> entities = inv.getArgument(0);
            entities.stream().mapToInt(StatBureauYearlyInflation::getInflationYear).forEach(storedYears::add);
            return entities.size();
        }).when(yearlyInflationService).upsertYearlyRecords(any());

        AnnualInflationScatterSeriesDto series = service.getAnnualInflationScatterData(2);

        assertThat(series.unavailableYears()).containsExactly(brokenYear);
        assertThat(series.fetchedFromApiYears()).containsExactly(goodYear);
        assertThat(series.points()).extracting(AnnualInflationPointDto::year)
                .containsExactly(goodYear);
        assertThat(series.points().get(0).source())
                .isEqualTo(AnnualInflationPointDto.SOURCE_API);
        assertThat(series.points().get(0).ratePercent()).isEqualTo(2.9);
        // Exactly one successful persist (the good year): an empty API answer never reaches the upsert
        verify(yearlyInflationService, times(1)).upsertYearlyRecords(any());
    }

    @Test
    @DisplayName("Ten-year window covers exactly the last ten completed years and excludes the running year")
    void tenYearWindowCoversCompletedYearsOnly() {
        stubHeadlineLookup();
        List<Integer> window = expectedWindow(10);
        window.forEach(year -> seedDatabase(year, 3.5));

        AnnualInflationScatterSeriesDto series = service.getAnnualInflationScatterData(10);

        assertThat(series.requestedYears()).isEqualTo(10);
        assertThat(series.points()).hasSize(10);
        assertThat(series.fromYear()).isEqualTo(Year.now().getValue() - 10);
        assertThat(series.toYear()).isEqualTo(Year.now().getValue() - 1);
        assertThat(series.points()).extracting(AnnualInflationPointDto::year)
                .doesNotContain(Year.now().getValue());
    }

    @Test
    @DisplayName("A non-positive range is rejected before any database or API access")
    void nonPositiveRangeIsRejected() {
        assertThatThrownBy(() -> service.getAnnualInflationScatterData(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("numberOfYears");
        assertThatThrownBy(() -> service.getAnnualInflationScatterData(-3))
                .isInstanceOf(IllegalArgumentException.class);

        verify(yearlyInflationService, never()).findHeadlineAnnualCpi(any(Currency.class), anyInt());
        verify(yearlyInflationService, never()).fetchFromStatBureau(any(Currency.class), anyInt());
    }
}
