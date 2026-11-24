package com.dnu.sfm.service.impl;

import com.dnu.sfm.dto.AnnualInflationPointDto;
import com.dnu.sfm.dto.AnnualInflationScatterSeriesDto;
import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.WorldBankInflation;
import com.dnu.sfm.service.WorldBankInflationChartService;
import com.dnu.sfm.service.WorldBankInflationService;
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
 * The World Bank chart composes the annual-inflation feature end to end for a
 * single, fixed economy - the Euro Area ({@link Currency#EUR}, World Bank
 * economy code "XC", the only currency this chart covers): database reads go
 * through {@link WorldBankInflationService#findHeadlineAnnualCpi(Currency, int)},
 * remote fetches through
 * {@link WorldBankInflationService#fetchFromWorldBank(Currency, int)} (the
 * single-economy fetch, so no remote calls are wasted on the other tracked
 * currencies) and the "store for future use" step through the transactional
 * bulk upsert {@link WorldBankInflationService#upsertAnnualRecords(List)}.
 *
 * <p>Those methods are called from a separate bean so their
 * {@code @Transactional} semantics stay intact (no Spring self-invocation), and
 * the remote call happens outside any database transaction - no SQLite
 * connection is held while waiting on the World Bank API.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorldBankInflationChartServiceImpl implements WorldBankInflationChartService {

    /** The chart is limited to the Euro Area economy, as requested. */
    static final Currency CHART_CURRENCY = Currency.EUR;

    private final WorldBankInflationService inflationService;

    @Override
    public AnnualInflationScatterSeriesDto getAnnualInflationScatterData(int numberOfYears) {
        if (numberOfYears < 1) {
            throw new IllegalArgumentException(
                    "numberOfYears must be positive, got " + numberOfYears);
        }

        // The World Bank publishes a year's annual figure only after the year
        // is over, so the chart window always ends at last year.
        int currentYear = Year.now().getValue();
        List<Integer> wantedYears = new ArrayList<>();
        for (int year = currentYear - numberOfYears; year <= currentYear - 1; year++) {
            wantedYears.add(year);
        }
        int fromYear = wantedYears.get(0);
        int toYear = wantedYears.get(wantedYears.size() - 1);

        // 1. Database first: collect every year that already has the stored EUR
        //    annual inflation row.
        Map<Integer, Double> rateByYear = new LinkedHashMap<>();
        List<Integer> missingYears = new ArrayList<>();
        List<Integer> servedFromDatabase = new ArrayList<>();
        for (int year : wantedYears) {
            Optional<WorldBankInflation> stored =
                    inflationService.findHeadlineAnnualCpi(CHART_CURRENCY, year);
            if (stored.isPresent()) {
                rateByYear.put(year, stored.get().getRatePercent().doubleValue());
                servedFromDatabase.add(year);
            } else {
                missingYears.add(year);
            }
        }
        if (!missingYears.isEmpty()) {
            log.info("World Bank annual inflation chart {} {}: {} year(s) served from DB, fetching missing {}",
                    CHART_CURRENCY, fromYear + "-" + toYear, servedFromDatabase.size(), missingYears);
        } else {
            log.info("World Bank annual inflation chart {} {}: all {} year(s) served from DB, no API call needed",
                    CHART_CURRENCY, fromYear + "-" + toYear, servedFromDatabase.size());
        }

        // 2. Fetch every missing year from the World Bank API and store it for future use.
        List<Integer> fetchedFromApi = new ArrayList<>();
        List<Integer> unavailableYears = new ArrayList<>();
        for (int year : missingYears) {
            if (fetchAndStore(year)) {
                Optional<WorldBankInflation> stored =
                        inflationService.findHeadlineAnnualCpi(CHART_CURRENCY, year);
                if (stored.isPresent()) {
                    rateByYear.put(year, stored.get().getRatePercent().doubleValue());
                    fetchedFromApi.add(year);
                } else {
                    // The API answered but produced no usable EUR row (e.g. the
                    // value is not published yet): treat the year as unusable
                    // instead of plotting a wrong value.
                    log.warn("Stored World Bank records for {} {} contain no usable annual row",
                            CHART_CURRENCY, year);
                    unavailableYears.add(year);
                }
            } else {
                unavailableYears.add(year);
            }
        }

        // 3. Chart points, ascending by year; unresolvable years are absent.
        List<AnnualInflationPointDto> points = wantedYears.stream()
                .filter(rateByYear::containsKey)
                .map(year -> new AnnualInflationPointDto(
                        year,
                        rateByYear.get(year),
                        fetchedFromApi.contains(year)
                                ? AnnualInflationPointDto.SOURCE_API
                                : AnnualInflationPointDto.SOURCE_DATABASE))
                .toList();

        return new AnnualInflationScatterSeriesDto(
                CHART_CURRENCY.name(),
                numberOfYears, fromYear, toYear,
                points, servedFromDatabase, fetchedFromApi, unavailableYears);
    }

    /**
     * One year = one remote call + one transactional bulk upsert. The remote
     * call happens outside any database transaction (the inflation service is
     * called through its Spring proxy), so no connection is held while waiting
     * on the World Bank. Any failure is contained: the year is reported as
     * unavailable instead of breaking the whole chart.
     */
    private boolean fetchAndStore(int year) {
        try {
            List<WorldBankInflation> entities =
                    inflationService.fetchFromWorldBank(CHART_CURRENCY, year);
            if (entities.isEmpty()) {
                log.warn("World Bank API returned no annual inflation data for {} {}", CHART_CURRENCY, year);
                return false;
            }
            int stored = inflationService.upsertAnnualRecords(entities);
            log.info("Fetched and stored {} World Bank annual inflation record(s) for {} {}",
                    stored, CHART_CURRENCY, year);
            return true;
        } catch (Exception ex) {
            log.error("Failed to fetch/store World Bank annual inflation for {} {}",
                    CHART_CURRENCY, year, ex);
            return false;
        }
    }
}
