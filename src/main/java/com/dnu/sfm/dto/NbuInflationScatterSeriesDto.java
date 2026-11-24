package com.dnu.sfm.dto;

import java.util.List;

/**
 * Complete chart payload for the NBU annual inflation scatter plot.
 *
 * @param requestedYears          how many past years the user asked for (2, 5 or 10)
 * @param fromYear                oldest year of the requested window (inclusive)
 * @param toYear                  newest year of the requested window (inclusive; always
 *                                the last completed year, the running year is excluded
 *                                because its annual rate is not published yet)
 * @param points                  plot points, ascending by year; years without usable
 *                                data are simply absent
 * @param servedFromDatabaseYears years whose headline annual CPI row was already
 *                                stored locally (no remote call was made for them)
 * @param fetchedFromApiYears     years that were missing in the database and were
 *                                fetched from the NBU API during this request (they
 *                                have been stored for future use)
 * @param unavailableYears        years for which no usable data could be resolved
 *                                even after calling the NBU API
 */
public record NbuInflationScatterSeriesDto(
        int requestedYears,
        int fromYear,
        int toYear,
        List<NbuAnnualInflationPointDto> points,
        List<Integer> servedFromDatabaseYears,
        List<Integer> fetchedFromApiYears,
        List<Integer> unavailableYears) {
}
