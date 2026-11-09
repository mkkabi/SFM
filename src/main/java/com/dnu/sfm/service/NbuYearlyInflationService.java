package com.dnu.sfm.service;

import com.dnu.sfm.model.NbuYearlyInflation;

import java.util.List;
import java.util.Optional;

/**
 * Business logic for the NBU yearly inflation feature: fetching annual records
 * from the NBU StatService, persisting them transactionally, and querying them
 * for the yearly inflation view.
 */
public interface NbuYearlyInflationService {

    /**
     * Fetches all annual inflation records for the given calendar year from the
     * NBU API and converts them to entities. Performs no database access.
     *
     * @return the fetched entities; empty if the NBU API returned no data
     * @throws IllegalStateException if a fetched record cannot be converted
     */
    List<NbuYearlyInflation> fetchFromNbu(int year);

    /**
     * Persists the given entities as one atomic transaction: rows that already
     * exist for the year (same inflation year, index type and commodity group)
     * are updated in place, the rest are inserted.
     *
     * @return the number of processed input records
     */
    int upsertYearlyRecords(List<NbuYearlyInflation> entities);

    /** Records for the view layer, sorted by commodity group then index type. */
    List<NbuYearlyInflation> getRecordsForYear(int year);

    /** Total number of stored records for one calendar year. */
    long countByYear(int year);

    /**
     * The headline national annual CPI row (index type "DTPY_", commodity group
     * "Total") for the highlight banner - e.g. +5.10% for 2024.
     */
    Optional<NbuYearlyInflation> findHeadlineAnnualCpi(int year);
}
