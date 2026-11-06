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
                        columnNames = {"record_date", "index_type", "commodity_group"}
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
     */
    @Column(name = "index_type", length = 20)
    private String indexType;

    /**
     * Commodity group name (e.g., "Total").
     */
    @Column(name = "commodity_group", length = 100)
    private String commodityGroup;

    /**
     * Raw API identifier from NBU (e.g., "prices_price_cpi_").
     */
    @Column(name = "id_api", length = 50)
    private String idApi;

    public NbuMonthlyInflation(LocalDate recordDate, BigDecimal ratePercent, String indexType, String commodityGroup, String idApi) {
        this.recordDate = recordDate;
        this.ratePercent = ratePercent != null ? ratePercent.setScale(4, RoundingMode.HALF_EVEN) : BigDecimal.ZERO;
        this.indexType = indexType;
        this.commodityGroup = commodityGroup;
        this.idApi = idApi;
    }

    /**
     * Factory method to convert an NBU monthly DTO into a persistent entity.
     */
    public static NbuMonthlyInflation fromDto(NbuMonthlyInflationRecordDto dto) {
        YearMonth ym = dto.toYearMonth();
        LocalDate date = (ym != null) ? ym.atDay(1) : LocalDate.now().withDayOfMonth(1);

        return new NbuMonthlyInflation(
                date,
                dto.toRatePercent(),
                dto.indexType(),
                dto.commodityGroup(),
                dto.idApi()
        );
    }
}