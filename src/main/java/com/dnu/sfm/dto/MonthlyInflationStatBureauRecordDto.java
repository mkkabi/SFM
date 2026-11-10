package com.dnu.sfm.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data Transfer Object representing a monthly inflation record.
 * Supports direct Jackson deserialization of Microsoft ASP.NET timestamps (/Date(...)/),
 * ISO date strings, and flexible numeric or percentage string payloads.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MonthlyInflationStatBureauRecordDto(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        @JsonProperty("Month")
        LocalDate month,

        @JsonProperty("InflationRate")
        BigDecimal ratePercent,

        @JsonProperty("InflationRateFormatted")
        String formattedRate
) {

    private static final Pattern ASPNET_DATE_PATTERN = Pattern.compile("^/Date\\((-?\\d+)(?:[+-]\\d+)?\\)/$");
    private static final MathContext PRECISION_CONTEXT = MathContext.DECIMAL64;
    private static final int RATE_SCALE = 4;
    private static final int DISPLAY_SCALE = 2;

    /**
     * Compact constructor: normalizes numeric scale to 4 decimal places (matching
     * the persistent model) and guarantees a valid formatted rate string.
     */
    public MonthlyInflationStatBureauRecordDto {
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
     * Jackson factory constructor enabling automatic deserialization across legacy ASP.NET
     * endpoints (e.g. StatBureau) and modern ISO JSON payloads without throwing format exceptions.
     */
    @JsonCreator
    public static MonthlyInflationStatBureauRecordDto fromJson(
            @JsonProperty("Month") Object monthRaw,
            @JsonProperty("InflationRate") Object rateRaw,
            @JsonProperty("InflationRateFormatted") String formattedRateRaw
    ) {
        LocalDate parsedMonth = parseDate(monthRaw);
        BigDecimal parsedRate = parseRate(rateRaw);
        return new MonthlyInflationStatBureauRecordDto(parsedMonth, parsedRate, formattedRateRaw);
    }

    /**
     * Converts the date component directly to {@link YearMonth} for persistence into
     * {@code MonthlyInflationRate.recordDate}.
     */
    public YearMonth toYearMonth() {
        return month != null ? YearMonth.from(month) : null;
    }

    /**
     * Returns the monthly growth multiplier factor (1 + rate / 100), useful for
     * chaining cumulative purchasing power deflators.
     */
    public BigDecimal toMultiplier() {
        return BigDecimal.ONE.add(
                ratePercent.divide(BigDecimal.valueOf(100), PRECISION_CONTEXT),
                PRECISION_CONTEXT
        );
    }

    private static LocalDate parseDate(Object raw) {
        if (raw == null) {
            return null;
        }

        // Millisecond timestamp (numeric)
        if (raw instanceof Number num) {
            return Instant.ofEpochMilli(num.longValue())
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                    .withDayOfMonth(1);
        }

        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return null;
        }

        // ASP.NET JSON date: /Date(1483228800000)/ or /Date(1483228800000+0200)/
        Matcher matcher = ASPNET_DATE_PATTERN.matcher(text);
        if (matcher.matches()) {
            long epochMillis = Long.parseLong(matcher.group(1));
            return Instant.ofEpochMilli(epochMillis)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                    .withDayOfMonth(1);
        }

        // Standard ISO date: "yyyy-MM-dd"
        if (text.length() >= 10 && text.charAt(4) == '-' && text.charAt(7) == '-') {
            try {
                return LocalDate.parse(text.substring(0, 10)).withDayOfMonth(1);
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private static BigDecimal parseRate(Object raw) {
        if (raw == null) {
            return BigDecimal.ZERO;
        }

        if (raw instanceof Number num) {
            return BigDecimal.valueOf(num.doubleValue());
        }

        String text = raw.toString()
                .replace("\"", "")
                .replace("'", "")
                .replace("%", "")
                .replace(',', '.')
                .trim();

        if (text.isEmpty()) {
            return BigDecimal.ZERO;
        }

        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }
}