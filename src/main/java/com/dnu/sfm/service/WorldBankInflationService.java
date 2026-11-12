package com.dnu.sfm.service;

import com.dnu.sfm.model.WorldBankInflation;

import java.util.List;
import java.util.Optional;

/**
 * Business logic for the World Bank annual inflation feature: fetching annual
 * records from the World Bank Open Data API, persisting them transactionally,
 * and querying them for the annual inflation view.
 */
public interface WorldBankInflationService {

    /**
     * Fetches the annual inflation records for the given calendar year from the
     * World Bank API for every tracked currency (UAH, EUR, USD) and converts
     * them to entities. Performs no database access.
     *
     * @return the fetched entities; empty if the World Bank API returned no data
     * @throws IllegalStateException if a fetched record cannot be converted
     */
    List<WorldBankInflation> fetchFromWorldBank(int year);

    /**
     * Persists the given entities as one atomic transaction: rows that already
     * exist for the year (same inflation year and currency) are updated in
     * place, the rest are inserted.
     *
     * @return the number of processed input records
     */
    int upsertAnnualRecords(List<WorldBankInflation> entities);

    /** Records for the view layer, sorted by currency (UAH, EUR, USD). */
    List<WorldBankInflation> getRecordsForYear(int year);

    /** Total number of stored records for one calendar year. */
    long countByYear(int year);

    /**
     * The headline Ukraine annual CPI row for the highlight banner
     * - e.g. +9.58% for 2024.
     */
    Optional<WorldBankInflation> findHeadlineAnnualCpi(int year);
}
