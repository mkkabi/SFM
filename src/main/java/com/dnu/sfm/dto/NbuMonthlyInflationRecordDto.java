package com.dnu.sfm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.YearMonth;

/**
 * Data Transfer Object representing a monthly inflation record returned by the NBU StatService API.
 * Target endpoint: https://bank.gov.ua/NBUStatService/v1/statdirectory/inflation?period=m&date=YYYYMM&json
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NbuMonthlyInflationRecordDto(
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
        String commodityGroup,

        @JsonProperty("ku")
        String regionCode,

        @JsonProperty("mcrk110")
        String mcrk110,

        @JsonProperty("tzep")
        String indexType,

        @JsonProperty("value")
        BigDecimal value
) {

    private static final MathContext MC = MathContext.DECIMAL64;

    /**
     * Checks if this record represents the headline national Month-over-Month (MoM)
     * Consumer Price Index (CPI) change across all commodity groups in Ukraine.
     */
    public boolean isNationalMonthlyCpi() {
        return "prices_price_cpi_".equalsIgnoreCase(idApi)
                && "Total".equalsIgnoreCase(commodityGroup)
                && regionCode == null
                && "PCPM_".equalsIgnoreCase(indexType);
    }

    /**
     * Checks if this record represents the headline national Year-over-Year (YoY)
     * Consumer Price Index (CPI) change for that specific month compared to the same month last year.
     */
    public boolean isNationalYoYCpi() {
        return "prices_price_cpi_".equalsIgnoreCase(idApi)
                && "Total".equalsIgnoreCase(commodityGroup)
                && regionCode == null
                && "DTPY_".equalsIgnoreCase(indexType);
    }

    /**
     * Checks if this record represents the headline national Core Inflation rate (MoM).
     */
    public boolean isNationalMonthlyCoreInflation() {
        return "prices_price_ci_".equalsIgnoreCase(idApi)
                && "Total".equalsIgnoreCase(commodityGroup)
                && regionCode == null
                && "PCPM_".equalsIgnoreCase(indexType);
    }

    /**
     * Parses NBU date formats ("20230101", "202301", "01.01.2023", or "2023-01-01") into {@link YearMonth}.
     */
    public YearMonth toYearMonth() {
        if (dt == null || dt.isBlank()) {
            return null;
        }
        String clean = dt.trim();

        if (clean.matches("\\d{6,8}")) {
            return YearMonth.of(
                    Integer.parseInt(clean.substring(0, 4)),
                    Integer.parseInt(clean.substring(4, 6))
            );
        }

        if (clean.contains(".")) {
            String[] parts = clean.split("\\.");
            if (parts.length == 3) {
                return YearMonth.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]));
            }
        }

        if (clean.contains("-")) {
            String[] parts = clean.split("-");
            if (parts.length >= 2) {
                return YearMonth.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            }
        }

        return null;
    }

    /**
     * Returns the monthly growth percentage rate directly (e.g. 0.7 for +0.7%).
     */
    public BigDecimal toRatePercent() {
        return value != null ? value : BigDecimal.ZERO;
    }

    /**
     * Returns the purchasing power monthly deflator factor: 1 / (1 + ratePercent / 100).
     * Used to calculate the real value of cash balances after monthly inflation.
     */
    public BigDecimal toPurchasingPowerFactor() {
        if (value == null) {
            return BigDecimal.ONE;
        }
        BigDecimal rateFraction = value.divide(BigDecimal.valueOf(100), MC);
        return BigDecimal.ONE.divide(BigDecimal.ONE.add(rateFraction, MC), MC);
    }
}