package com.dnu.sfm.service.impl;

import com.dnu.sfm.dto.AnnualInflationPointDto;
import com.dnu.sfm.dto.AnnualInflationScatterSeriesDto;
import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.StatBureauYearlyInflation;
import com.dnu.sfm.service.StatBureauInflationChartService;
import com.dnu.sfm.service.StatBureauYearlyInflationService;
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
 * The StatBureau chart composes the yearly-inflation feature end to end for a
 * single, fixed economy - the United States ({@link Currency#USD}, the only
 * currency this chart covers): database reads go through
 * {@link StatBureauYearlyInflationService#findHeadlineAnnualCpi(Currency, int)},
 * remote fetches through
 * {@link StatBureauYearlyInflationService#fetchFromStatBureau(Currency, int)}
 * and the "store for future use" step through the transactional bulk upsert
 * {@link StatBureauYearlyInflationService#upsertYearlyRecords(List)}.
 *
 * <p>Those methods are called from a separate bean so their
 * {@code @Transactional} semantics stay intact (no Spring self-invocation), and
 * the remote call happens outside any database transaction - no SQLite
 * connection is held while waiting on the StatBureau API.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatBureauInflationChartServiceImpl implements StatBureauInflationChartService {

    /** The chart is limited to the US economy, as requested. */
    static final Currency CHART_CURRENCY = Currency.USD;

    private final StatBureauYearlyInflationService yearlyInflationService;

    @Override
    public AnnualInflationScatterSeriesDto getAnnualInflationScatterData(int numberOfYears) {
        if (numberOfYears < 1) {
            throw new IllegalArgumentException(
                    "numberOfYears must be positive, got " + numberOfYears);
        }

        // The annual (Dec-to-Dec) CPI of the running year is only final once the
        // year is over, so the chart window always ends at last year.
        int currentYear = Year.now().getValue();
        List<Integer> wantedYears = new ArrayList<>();
        for (int year = currentYear - numberOfYears; year <= currentYear - 1; year++) {
            wantedYears.add(year);
        }
        int fromYear = wantedYears.get(0);
        int toYear = wantedYears.get(wantedYears.size() - 1);

        // 1. Database first: collect every year that already has the stored USD
        //    annual inflation row.
        Map<Integer, Double> rateByYear = new LinkedHashMap<>();
        List<Integer> missingYears = new ArrayList<>();
        List<Integer> servedFromDatabase = new ArrayList<>();
        for (int year : wantedYears) {
            Optional<StatBureauYearlyInflation> stored =
                    yearlyInflationService.findHeadlineAnnualCpi(CHART_CURRENCY, year);
            if (stored.isPresent()) {
                rateByYear.put(year, stored.get().getRatePercent().doubleValue());
                servedFromDatabase.add(year);
            } else {
                missingYears.add(year);
            }
        }
        if (!missingYears.isEmpty()) {
            log.info("StatBureau annual inflation chart {} {}: {} year(s) served from DB, fetching missing {}",
                    CHART_CURRENCY, fromYear + "-" + toYear, servedFromDatabase.size(), missingYears);
        } else {
            log.info("StatBureau annual inflation chart {} {}: all {} year(s) served from DB, no API call needed",
                    CHART_CURRENCY, fromYear + "-" + toYear, servedFromDatabase.size());
        }

        // 2. Fetch every missing year from the StatBureau API and store it for future use.
        List<Integer> fetchedFromApi = new ArrayList<>();
        List<Integer> unavailableYears = new ArrayList<>();
        for (int year : missingYears) {
            if (fetchAndStore(year)) {
                Optional<StatBureauYearlyInflation> stored =
                        yearlyInflationService.findHeadlineAnnualCpi(CHART_CURRENCY, year);
                if (stored.isPresent()) {
                    rateByYear.put(year, stored.get().getRatePercent().doubleValue());
                    fetchedFromApi.add(year);
                } else {
                    // The API answered but produced no usable USD row:
                    // treat the year as unusable instead of plotting a wrong value.
                    log.warn("Stored StatBureau records for {} {} contain no usable annual row",
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
     * call happens outside any database transaction (the yearly service is
     * called through its Spring proxy), so no connection is held while waiting
     * on StatBureau. Any failure is contained: the year is reported as
     * unavailable instead of breaking the whole chart.
     */
    private boolean fetchAndStore(int year) {
        try {
            List<StatBureauYearlyInflation> entities =
                    yearlyInflationService.fetchFromStatBureau(CHART_CURRENCY, year);
            if (entities.isEmpty()) {
                log.warn("StatBureau API returned no annual inflation data for {} {}", CHART_CURRENCY, year);
                return false;
            }
            int stored = yearlyInflationService.upsertYearlyRecords(entities);
            log.info("Fetched and stored {} StatBureau annual inflation record(s) for {} {}",
                    stored, CHART_CURRENCY, year);
            return true;
        } catch (Exception ex) {
            log.error("Failed to fetch/store StatBureau annual inflation for {} {}",
                    CHART_CURRENCY, year, ex);
            return false;
        }
    }
}
