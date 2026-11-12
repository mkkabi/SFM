package com.dnu.sfm.service;

import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.StatBureauMonthlyInflation;

import java.util.List;
import java.util.Optional;

/**
 * Business logic for the StatBureau monthly inflation feature: fetching the
 * monthly history from the StatBureau.org API, persisting it transactionally,
 * and querying it for the monthly inflation view (currency + calendar year).
 */
public interface StatBureauInflationService {

    /**
     * Fetches the full available monthly inflation history of the given
     * currency's economy from the StatBureau API and converts it to entities.
     * Performs no database access.
     *
     * @return the fetched entities, newest month first; empty if the StatBureau
     *         API returned no data
     */
    List<StatBureauMonthlyInflation> fetchFromStatBureau(Currency currency);

    /**
     * Persists the given entities as one atomic transaction: rows that already
     * exist for the currency (same record month and currency) are updated in
     * place, the rest are inserted.
     *
     * @return the number of processed input records
     */
    int upsertMonthlyRecords(List<StatBureauMonthlyInflation> entities);

    /** Stored months of one currency for one calendar year, oldest month first. */
    List<StatBureauMonthlyInflation> getRecordsForYear(Currency currency, int year);

    /** Number of stored months of one currency for one calendar year. */
    long countByYear(Currency currency, int year);

    /** Total number of stored records of one currency (full cached history). */
    long countByCurrency(Currency currency);

    /** The newest stored month of one currency, for the data-freshness hint. */
    Optional<StatBureauMonthlyInflation> findLatestRecord(Currency currency);

    /**
     * The headline Month-over-Month CPI row for the highlight banner: the newest
     * stored month of the selected year for the given currency.
     */
    Optional<StatBureauMonthlyInflation> findHeadlineMonthlyCpi(Currency currency, int year);
}
