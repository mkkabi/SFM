package com.dnu.sfm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Year;

/**
 * Data Transfer Object representing an annual inflation record returned by the NBU StatService API.
 * Target endpoint: <a href="https://bank.gov.ua/NBUStatService/v1/statdirectory/inflation?period=y&date=YYYYMM&json">...</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NbuYearlyInflationRecordDto(
        @JsonProperty("dt")
        String dt,

        @JsonProperty("txt")
        String txt,

        @JsonProperty("txten")
        String txten,

        @JsonProperty("id_api")
        String idApi,

        @JsonProperty("leveli")
        String leveli,

        @JsonProperty("parent")
        String parent,

        @JsonProperty("freq")
        String freq,

        @JsonProperty("mcrd081")
        String mcrd081,

        @JsonProperty("ku")
        String ku,

        @JsonProperty("mcrk110")
        String mcrk110,

        @JsonProperty("tzep")
        String tzep,

        @JsonProperty("value")
        BigDecimal value
) {

    /**
     * Checks if this record represents the headline national annual Consumer Price Index (CPI)
     * growth rate across all commodity baskets in Ukraine (Year-over-Year).
     */
    public boolean isNationalAnnualCpi() {
        return "prices_price_cpi_".equalsIgnoreCase(idApi)
                && "Total".equalsIgnoreCase(mcrd081)
                && ku == null
                && "DTPY_".equalsIgnoreCase(tzep);
    }

    /**
     * Extracts the calendar year from the date string ("20230101" -> 2023).
     */
    public Year toYear() {
        if (dt == null || dt.isBlank()) {
            return null;
        }
        String clean = dt.trim();
        if (clean.length() >= 4 && clean.substring(0, 4).matches("\\d{4}")) {
            return Year.of(Integer.parseInt(clean.substring(0, 4)));
        }
        return null;
    }

    /**
     * Extracts the calendar year the annual figure DESCRIBES.
     * <p>
     * The NBU annual dataset dates every row with its publication date, and the
     * endpoint only accepts January queries ("date=YYYY01"), so each row is
     * dated January 1 of the year after the one it covers: the row dt=20250101
     * (fetched with date=202501) carries the annual (Dec-to-Dec) figure of 2024
     * (+12.0%), dt=20260101 carries 2025 (+8.0%), dt=20230101 carries 2022
     * (+26.6%), etc. Persisting the raw publication year would shift the whole
     * series one year into the future, hence the one-year shift here.
     */
    public Year toCoveredYear() {
        Year publicationYear = toYear();
        return publicationYear != null ? publicationYear.minusYears(1) : null;
    }

    /**
     * Returns the annual inflation growth rate as a percentage.
     * NBU "DTPY_" index represents percentage change directly (e.g. 26.6 for +26.6%).
     */
    public BigDecimal toRatePercent() {
        return value != null ? value : BigDecimal.ZERO;
    }
}