package com.dnu.sfm.service.impl;

import com.dnu.sfm.dto.NbuAnnualInflationPointDto;
import com.dnu.sfm.dto.NbuInflationScatterSeriesDto;
import com.dnu.sfm.model.NbuYearlyInflation;
import com.dnu.sfm.service.NbuInflationChartService;
import com.dnu.sfm.service.NbuYearlyInflationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The chart composes the yearly-inflation feature end to end: database reads go
 * through {@link NbuYearlyInflationService#findHeadlineAnnualCpi(int)}, remote
 * fetches through {@link NbuYearlyInflationService#fetchFromNbu(int)} and the
 * "store for future use" step through the transactional bulk upsert
 * {@link NbuYearlyInflationService#upsertYearlyRecords(List)}.
 *
 * <p>Those methods are called from a separate bean so their
 * {@code @Transactional} semantics stay intact (no Spring self-invocation), and
 * the remote call happens outside any database transaction - no SQLite
 * connection is held while waiting on the NBU API.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NbuInflationChartServiceImpl implements NbuInflationChartService {

    private final NbuYearlyInflationService yearlyInflationService;

    @Override
    public NbuInflationScatterSeriesDto getAnnualInflationScatterData(int numberOfYears) {
        if (numberOfYears < 1) {
            throw new IllegalArgumentException(
                    "numberOfYears must be positive, got " + numberOfYears);
        }

        // Annual (Dec-to-Dec) CPI of the running year is only published once the
        // year is over, so the chart window always ends at last year.
        int currentYear = Year.now().getValue();
        List<Integer> wantedYears = new ArrayList<>();
        for (int year = currentYear - numberOfYears; year <= currentYear - 1; year++) {
            wantedYears.add(year);
        }
        int fromYear = wantedYears.get(0);
        int toYear = wantedYears.get(wantedYears.size() - 1);

        // 1. Database first: collect every year that already has the headline
        //    national annual CPI row (DTPY_ / Total) stored.
        Map<Integer, NbuYearlyInflation> headlineByYear = new LinkedHashMap<>();
        List<Integer> missingYears = new ArrayList<>();
        List<Integer> servedFromDatabase = new ArrayList<>();
        for (int year : wantedYears) {
            Optional<NbuYearlyInflation> stored = yearlyInflationService.findHeadlineAnnualCpi(year);
            if (stored.isPresent()) {
                headlineByYear.put(year, stored.get());
                servedFromDatabase.add(year);
            } else {
                missingYears.add(year);
            }
        }
        if (!missingYears.isEmpty()) {
            log.info("Annual inflation chart {}: {} year(s) served from DB, fetching missing {}",
                    fromYear + "-" + toYear, servedFromDatabase.size(), missingYears);
        } else {
            log.info("Annual inflation chart {}: all {} year(s) served from DB, no API call needed",
                    fromYear + "-" + toYear, servedFromDatabase.size());
        }

        // 2. Fetch every missing year from the NBU API and store it for future use.
        List<Integer> fetchedFromApi = new ArrayList<>();
        List<Integer> unavailableYears = new ArrayList<>();
        for (int year : missingYears) {
            if (fetchAndStore(year)) {
                Optional<NbuYearlyInflation> stored = yearlyInflationService.findHeadlineAnnualCpi(year);
                if (stored.isPresent()) {
                    headlineByYear.put(year, stored.get());
                    fetchedFromApi.add(year);
                } else {
                    // The API answered but produced no national Total/DTPY_ row:
                    // treat the year as unusable instead of plotting a wrong value.
                    log.warn("Stored NBU records for {} contain no headline annual CPI row", year);
                    unavailableYears.add(year);
                }
            } else {
                unavailableYears.add(year);
            }
        }

        // 3. Chart points, ascending by year; unresolvable years are absent.
        List<NbuAnnualInflationPointDto> points = wantedYears.stream()
                .filter(headlineByYear::containsKey)
                .map(year -> new NbuAnnualInflationPointDto(
                        year,
                        headlineByYear.get(year).getRatePercent().doubleValue(),
                        fetchedFromApi.contains(year)
                                ? NbuAnnualInflationPointDto.SOURCE_API
                                : NbuAnnualInflationPointDto.SOURCE_DATABASE))
                .toList();

        return new NbuInflationScatterSeriesDto(
                numberOfYears, fromYear, toYear,
                points, servedFromDatabase, fetchedFromApi, unavailableYears);
    }

    /**
     * One year = one remote call + one transactional bulk upsert. The remote
     * call happens outside any database transaction (the yearly service is
     * called through its Spring proxy), so no connection is held while waiting
     * on NBU. Any failure is contained: the year is reported as unavailable
     * instead of breaking the whole chart.
     */
    private boolean fetchAndStore(int year) {
        try {
            List<NbuYearlyInflation> entities = yearlyInflationService.fetchFromNbu(year);
            if (entities.isEmpty()) {
                log.warn("NBU API returned no annual inflation data for {}", year);
                return false;
            }
            int stored = yearlyInflationService.upsertYearlyRecords(entities);
            log.info("Fetched and stored {} NBU annual inflation records for {}", stored, year);
            return true;
        } catch (Exception ex) {
            log.error("Failed to fetch/store NBU annual inflation for {}", year, ex);
            return false;
        }
    }
}
