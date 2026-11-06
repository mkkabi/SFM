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
     * Returns the annual inflation growth rate as a percentage.
     * NBU "DTPY_" index represents percentage change directly (e.g. 26.6 for +26.6%).
     */
    public BigDecimal toRatePercent() {
        return value != null ? value : BigDecimal.ZERO;
    }
}