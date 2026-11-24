package com.dnu.sfm.dto;

/**
 * One plotted point of the NBU annual inflation scatter chart: the headline
 * national annual CPI rate (index type "DTPY_", commodity group "Total") for a
 * single calendar year.
 *
 * @param year        calendar year of the record
 * @param ratePercent annual inflation rate in percent (e.g. 12.0 for +12.0%)
 * @param source      where the point came from: {@link #SOURCE_DATABASE} when the
 *                    year was already stored locally, or {@link #SOURCE_API} when
 *                    it was fetched from the NBU StatService during this request
 */
public record NbuAnnualInflationPointDto(int year, double ratePercent, String source) {

    /** Point was already present in the local database (no remote call needed). */
    public static final String SOURCE_DATABASE = "database";

    /** Point was missing in the database and was fetched from the NBU API this request. */
    public static final String SOURCE_API = "api";
}
