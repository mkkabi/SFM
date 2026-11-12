package com.dnu.sfm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Year;

/**
 * Data Transfer Object representing one data row of the World Bank API v2 for the
 * annual consumer price inflation indicator.
 * Target endpoint:
 * <a href="https://api.worldbank.org/v2/country/{country}/indicator/FP.CPI.TOTL.ZG?date=YYYY&format=json">...</a>
 * <p>
 * The raw JSON response is a two-element array: {@code [metadata_obj, [dataRow, ...]]}.
 * The client extracts the second element and maps every entry of it to this record.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorldBankInflationRecordDto(
        @JsonProperty("indicator")
        IndicatorRef indicator,

        @JsonProperty("country")
        CountryRef country,

        @JsonProperty("countryiso3code")
        String countryIso3Code,

        @JsonProperty("date")
        String date,

        @JsonProperty("value")
        BigDecimal value,

        @JsonProperty("unit")
        String unit,

        @JsonProperty("obs_status")
        String obsStatus,

        @JsonProperty("decimal")
        Integer decimal
) {

    /** Nested {@code "indicator": {"id": "FP.CPI.TOTL.ZG", "value": "Inflation, consumer prices (annual %)"}} object. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IndicatorRef(String id, String value) {
    }

    /** Nested {@code "country": {"id": "UA", "value": "Ukraine"}} object. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CountryRef(String id, String value) {
    }

    /**
     * Checks whether the API row carries an actual measurement. The World Bank
     * returns an explicit {@code null} value for years with no published data
     * (e.g. future years), which must not be persisted as a zero rate.
     */
    public boolean hasValue() {
        return value != null;
    }

    /**
     * Extracts the calendar year from the period string ("2024" -> 2024).
     *
     * @return the parsed year, or {@code null} if the date is missing/unparseable
     */
    public Year toYear() {
        if (date == null || date.isBlank()) {
            return null;
        }
        String clean = date.trim();
        if (clean.length() >= 4 && clean.substring(0, 4).matches("\\d{4}")) {
            return Year.of(Integer.parseInt(clean.substring(0, 4)));
        }
        return null;
    }

    /**
     * Returns the annual inflation growth rate as a percentage.
     * World Bank indicator "FP.CPI.TOTL.ZG" measures the annual percentage change
     * of the consumer price index (e.g. 9.58 for +9.58% in Ukraine, 2024).
     */
    public BigDecimal toRatePercent() {
        return value != null ? value : BigDecimal.ZERO;
    }
}
