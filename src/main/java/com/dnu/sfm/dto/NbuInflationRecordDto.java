package com.dnu.sfm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.YearMonth;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NbuInflationRecordDto(
        @JsonProperty("dt")
        String dt,

        @JsonProperty("txt")
        String txt,

        @JsonProperty("val")
        BigDecimal val,

        @JsonProperty("ku")
        String ku,

        @JsonProperty("tz")
        String tz
) {

    /**
     * Converts NBU's date string ("20261001", "202610", or "01.10.2026") into YearMonth.
     */
    public YearMonth toYearMonth() {
        if (dt == null || dt.isBlank()) {
            return null;
        }
        String clean = dt.trim();

        // Format: yyyyMMdd or yyyyMM (e.g. 20261001 or 202610)
        if (clean.matches("\\d{6,8}")) {
            int year = Integer.parseInt(clean.substring(0, 4));
            int month = Integer.parseInt(clean.substring(4, 6));
            return YearMonth.of(year, month);
        }

        // Format: dd.MM.yyyy (e.g. 01.10.2026)
        if (clean.contains(".")) {
            String[] parts = clean.split("\\.");
            if (parts.length == 3) {
                return YearMonth.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]));
            }
        }

        // Format: yyyy-MM-dd
        if (clean.contains("-")) {
            String[] parts = clean.split("-");
            if (parts.length >= 2) {
                return YearMonth.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            }
        }

        return null;
    }

    /**
     * Converts NBU CPI base index (e.g. 100.8%) into monthly inflation change (+0.80%).
     * If the value is already normalized (< 50.0), it is returned directly.
     */
    public BigDecimal toRatePercent() {
        if (val == null) {
            return BigDecimal.ZERO;
        }
        if (val.compareTo(new BigDecimal("50")) > 0) {
            return val.subtract(new BigDecimal("100"));
        }
        return val;
    }
}