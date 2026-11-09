package com.dnu.sfm.service;

import com.dnu.sfm.model.NbuMonthlyInflation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Business logic for the NBU monthly inflation feature: fetching records from
 * the NBU StatService, persisting them transactionally, and querying them for
 * the monthly inflation view (national vs. regional scope).
 */
public interface NbuMonthlyInflationService {

    /**
     * Fetches all monthly inflation records for the given month from the NBU API
     * and converts them to entities. Performs no database access.
     *
     * @return the fetched entities; empty if the NBU API returned no data
     * @throws IllegalStateException if a fetched record cannot be converted
     */
    List<NbuMonthlyInflation> fetchFromNbu(LocalDate targetDate);

    /**
     * Persists the given entities as one atomic transaction: rows that already
     * exist for the month (same date, index type, commodity group and region)
     * are updated in place, the rest are inserted.
     *
     * @return the number of processed input records
     */
    int upsertMonthlyRecords(List<NbuMonthlyInflation> entities);

    /**
     * Records for the view layer, sorted by commodity group then index type.
     *
     * @param regionScope {@code "all"} returns national + regional rows;
     *                    any other value returns national (Ukraine-wide) rows only
     */
    List<NbuMonthlyInflation> getRecordsForView(LocalDate recordDate, String regionScope);

    /** Total number of stored records (all scopes) for one month. */
    long countByRecordDate(LocalDate recordDate);

    /**
     * The headline national Month-over-Month CPI row (index type "PCPM_",
     * commodity group "Total", national scope) for the highlight banner.
     */
    Optional<NbuMonthlyInflation> findHeadlineCpi(LocalDate recordDate);
}
