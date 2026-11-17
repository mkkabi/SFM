package com.dnu.sfm.dto;

import com.dnu.sfm.model.NbuYearlyInflation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks in the NBU annual-dataset year convention: the endpoint only accepts
 * January queries and dates every row with its publication date (January 1 of
 * the year after the one it covers), so persistence must shift one year back.
 * <p>
 * Reference points (official Dec-to-Dec CPI figures): dt=20220101 -> 2021
 * (+10.0%), dt=20230101 -> 2022 (+26.6%), dt=20240101 -> 2023 (+5.1%),
 * dt=20250101 -> 2024 (+12.0%), dt=20260101 -> 2025 (+8.0%).
 */
class NbuYearlyInflationRecordDtoTest {

    private static NbuYearlyInflationRecordDto dto(String dt, String value) {
        return new NbuYearlyInflationRecordDto(
                dt, "Індекс споживчих цін", "Consumer price indices",
                "prices_price_cpi_", "1", null, "Y", "Total", null, "NULL",
                "DTPY_", new BigDecimal(value));
    }

    @Test
    @DisplayName("Row published 20250101 covers 2024 (+12.0%)")
    void rowPublishedIn2025Covers2024() {
        NbuYearlyInflation entity = NbuYearlyInflation.fromDto(dto("20250101", "12.0"));
        assertThat(entity.getInflationYear()).isEqualTo(2024);
        assertThat(entity.getRatePercent()).isEqualByComparingTo("12.0");
    }

    @Test
    @DisplayName("Row published 20240101 covers 2023 (+5.1%)")
    void rowPublishedIn2024Covers2023() {
        NbuYearlyInflation entity = NbuYearlyInflation.fromDto(dto("20240101", "5.1"));
        assertThat(entity.getInflationYear()).isEqualTo(2023);
    }

    @Test
    @DisplayName("Row published 20230101 covers 2022 (+26.6%)")
    void rowPublishedIn2023Covers2022() {
        NbuYearlyInflation entity = NbuYearlyInflation.fromDto(dto("20230101", "26.6"));
        assertThat(entity.getInflationYear()).isEqualTo(2022);
    }

    @Test
    @DisplayName("toCoveredYear mirrors toYear with a one-year shift")
    void toCoveredYearShiftsOneYearBack() {
        assertThat(dto("20260101", "8.0").toCoveredYear().getValue()).isEqualTo(2025);
        assertThat(dto("20260101", "8.0").toYear().getValue()).isEqualTo(2026);
    }

    @Test
    @DisplayName("Unparseable dates yield null years instead of a shifted guess")
    void unparseableDateYieldsNull() {
        assertThat(dto("", "8.0").toCoveredYear()).isNull();
        assertThat(dto("abcd", "8.0").toCoveredYear()).isNull();
    }
}
