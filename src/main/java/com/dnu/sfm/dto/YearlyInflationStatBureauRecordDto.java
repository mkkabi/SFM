package com.dnu.sfm.dto;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Data Transfer Object representing one annual inflation measurement taken from
 * the StatBureau.org API for a single calendar year.
 * <p>
 * StatBureau exposes annual figures through its
 * {@code calculate-inflation-rate-json} endpoint (January 1 to December 1 of the
 * requested year), which answers with a bare quoted percentage string such as
 * {@code "4.05"}. The StatBureau yearly client parses that payload and wraps it
 * in this record; when the calculator endpoint is unavailable the rate is
 * compounded from the cached monthly MoM history instead.
 * <p>
 * Unlike the monthly DTO this record needs no Jackson annotations or ASP.NET
 * date handling, because the yearly payload carries no timestamps - the year is
 * supplied by the caller.
 */
public record YearlyInflationStatBureauRecordDto(
        Integer year,
        BigDecimal ratePercent,
        String formattedRate
) {

    private static final MathContext PRECISION_CONTEXT = MathContext.DECIMAL64;
    private static final int RATE_SCALE = 4;
    private static final int DISPLAY_SCALE = 2;

    /**
     * Compact constructor: normalizes the numeric scale to 4 decimal places
     * (matching the persistent model) and guarantees a valid formatted rate
     * string for the view layer.
     */
    public YearlyInflationStatBureauRecordDto {
        ratePercent = (ratePercent != null)
                ? ratePercent.setScale(RATE_SCALE, RoundingMode.HALF_EVEN)
                : BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.HALF_EVEN);

        if (formattedRate == null || formattedRate.isBlank()) {
            formattedRate = ratePercent.setScale(DISPLAY_SCALE, RoundingMode.HALF_EVEN).toPlainString() + "%";
        } else {
            formattedRate = formattedRate.trim();
        }
    }

    /**
     * Returns the calendar year this record belongs to, or {@code null} when no
     * year was supplied, so that persistence can reject the record loudly.
     */
    public Integer toYear() {
        return year;
    }

    /**
     * Returns the annual inflation rate as a percentage
     * (e.g. 4.0500 for +4.05% over the calendar year).
     */
    public BigDecimal toRatePercent() {
        return ratePercent != null ? ratePercent : BigDecimal.ZERO;
    }

    /**
     * Returns the annual growth multiplier (1 + rate / 100), useful for chaining
     * multi-year purchasing power deflators.
     */
    public BigDecimal toMultiplier() {
        return BigDecimal.ONE.add(
                ratePercent.divide(BigDecimal.valueOf(100), PRECISION_CONTEXT),
                PRECISION_CONTEXT
        );
    }
}
