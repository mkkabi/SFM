package com.dnu.sfm.model;

import com.dnu.sfm.dto.NbuMonthlyInflationRecordDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "nbu_monthly_inflation",
        indexes = {
                @Index(name = "idx_nbu_monthly_date", columnList = "record_date")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_nbu_monthly_date_index_group",
                        columnNames = {"record_date", "index_type", "commodity_group",
                                "region_code", "id_api"}
                )
        }
)
public class NbuMonthlyInflation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * First day of the reporting month (e.g., 2026-10-01).
     */
    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    /**
     * Inflation rate as a percentage (e.g., 0.8000 for +0.80%).
     */
    @Column(name = "rate_percent", nullable = false, precision = 7, scale = 4)
    private BigDecimal ratePercent;

    /**
     * NBU index type indicator (e.g., "PCPM_" for MoM, "DTPY_" for YoY).
     * Part of the natural business key, therefore mandatory.
     */
    @Column(name = "index_type", length = 20, nullable = false)
    private String indexType;

    /**
     * Commodity group name (e.g., "Total").
     * Part of the natural business key, therefore mandatory.
     */
    @Column(name = "commodity_group", length = 100, nullable = false)
    private String commodityGroup;

    /**
     * NBU region code ("ku" from the API). Empty string = national (Ukraine-wide)
     * aggregate. A non-empty code identifies one of the regional breakdowns, so
     * (record_date, index_type, commodity_group, region_code) is unique.
     */
    @Column(name = "region_code", length = 10, nullable = false)
    private String regionCode = "";

    /**
     * Raw API identifier from NBU (e.g., "prices_price_cpi_"). Identifies the
     * index family (CPI vs. core inflation vs. PPI), which otherwise share the
     * same index_type / commodity_group / region values - part of the natural key.
     */
    @Column(name = "id_api", length = 50, nullable = false)
    private String idApi;

    public NbuMonthlyInflation(LocalDate recordDate, BigDecimal ratePercent, String indexType,
                               String commodityGroup, String regionCode, String idApi) {
        this.recordDate = recordDate;
        this.ratePercent = ratePercent != null ? ratePercent.setScale(4, RoundingMode.HALF_EVEN) : BigDecimal.ZERO;
        this.indexType = indexType;
        this.commodityGroup = commodityGroup;
        // Normalize null/blank to empty string so the unique constraint also covers
        // national (Ukraine-wide) rows - SQLite treats NULLs as distinct in unique indexes.
        this.regionCode = (regionCode != null && !regionCode.isBlank()) ? regionCode.trim() : "";
        this.idApi = idApi;
    }

    /**
     * Factory method to convert an NBU monthly DTO into a persistent entity.
     *
     * @throws IllegalStateException if the DTO carries an unparseable date or is
     *         missing the mandatory index type / commodity group, so that bad
     *         data fails loudly instead of being persisted under the wrong month.
     */
    public static NbuMonthlyInflation fromDto(NbuMonthlyInflationRecordDto dto) {
        YearMonth ym = dto.toYearMonth();
        if (ym == null) {
            throw new IllegalStateException(
                    "Unparseable NBU date '" + dto.dt() + "' - refusing to persist the record");
        }
        if (dto.indexType() == null || dto.indexType().isBlank()
                || dto.commodityGroup() == null || dto.commodityGroup().isBlank()) {
            throw new IllegalStateException(
                    "NBU record dated " + dto.dt() + " is missing index type or commodity group");
        }
        if (dto.idApi() == null || dto.idApi().isBlank()) {
            throw new IllegalStateException(
                    "NBU record dated " + dto.dt() + " is missing the API identifier (id_api)");
        }

        return new NbuMonthlyInflation(
                ym.atDay(1),
                dto.toRatePercent(),
                dto.indexType(),
                dto.commodityGroup(),
                dto.regionCode(),
                dto.idApi()
        );
    }
}