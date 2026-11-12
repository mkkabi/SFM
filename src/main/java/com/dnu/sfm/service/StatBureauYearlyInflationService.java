package com.dnu.sfm.service;

import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.StatBureauYearlyInflation;

import java.util.List;
import java.util.Optional;

/**
 * Business logic for the StatBureau yearly (annual) inflation feature: fetching
 * annual records from the StatBureau.org API, persisting them transactionally,
 * and querying them for the annual inflation view (currency + calendar year).
 */
public interface StatBureauYearlyInflationService {

    /**
     * Fetches the annual inflation rate of the given currency's economy for one
     * calendar year from the StatBureau API and converts it to an entity.
     * Performs no database access.
     *
     * @return the fetched entities (exactly one entry on success); empty if the
     *         StatBureau API returned no data for the requested year
     * @throws IllegalStateException if a fetched record cannot be converted
     */
    List<StatBureauYearlyInflation> fetchFromStatBureau(Currency currency, int year);

    /**
     * Persists the given entities as one atomic transaction: a row that already
     * exists for the currency (same inflation year and currency) is updated in
     * place, the rest are inserted.
     *
     * @return the number of processed input records
     */
    int upsertYearlyRecords(List<StatBureauYearlyInflation> entities);

    /** Stored records of one currency for one calendar year (0 or 1 row). */
    List<StatBureauYearlyInflation> getRecordsForYear(Currency currency, int year);

    /** Number of stored records of one currency for one calendar year. */
    long countByYear(Currency currency, int year);

    /** Total number of stored records of one currency (full cached history). */
    long countByCurrency(Currency currency);

    /** The newest stored year of one currency, for the data-freshness hint. */
    Optional<StatBureauYearlyInflation> findLatestRecord(Currency currency);

    /**
     * The headline annual CPI row of the selected year for the highlight banner
     * - e.g. +4.05% for UAH 2019.
     */
    Optional<StatBureauYearlyInflation> findHeadlineAnnualCpi(Currency currency, int year);
}
