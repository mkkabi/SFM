package com.dnu.sfm.dto;

/**
 * One plotted point of an annual inflation scatter chart: the annual consumer
 * price inflation rate of one economy (identified by its application currency)
 * for a single calendar year. Shared by the per-source annual inflation charts
 * (StatBureau, World Bank) which all plot the same shape of data.
 *
 * @param year        calendar year of the record
 * @param ratePercent annual inflation rate in percent (e.g. 2.89 for +2.89%)
 * @param source      where the point came from: {@link #SOURCE_DATABASE} when the
 *                    year was already stored locally, or {@link #SOURCE_API} when
 *                    it was fetched from the provider API during this request
 */
public record AnnualInflationPointDto(int year, double ratePercent, String source) {

    /** Point was already present in the local database (no remote call needed). */
    public static final String SOURCE_DATABASE = "database";

    /** Point was missing in the database and was fetched from the provider API this request. */
    public static final String SOURCE_API = "api";
}
