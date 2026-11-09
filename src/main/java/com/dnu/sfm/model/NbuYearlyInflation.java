package com.dnu.sfm.model;

import com.dnu.sfm.dto.NbuYearlyInflationRecordDto;
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
import java.time.Year;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "nbu_yearly_inflation",
        indexes = {
                @Index(name = "idx_nbu_yearly_year", columnList = "inflation_year")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_nbu_yearly_year_index_group",
                        columnNames = {"inflation_year", "tzep", "mcrd081"}
                )
        }
)
public class NbuYearlyInflation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Calendar year of the inflation record (e.g., 2025).
     */
    @Column(name = "inflation_year", nullable = false)
    private Integer inflationYear;

    /**
     * Annual inflation rate percentage (e.g., 12.3000 for +12.30%).
     */
    @Column(name = "rate_percent", nullable = false, precision = 7, scale = 4)
    private BigDecimal ratePercent;

    /**
     * NBU index type code (e.g., "DTPY_" for the year-over-year rate,
     * "PCCP_" for the Dec-to-Dec index level).
     * Part of the natural business key, therefore mandatory.
     */
    @Column(name = "tzep", length = 20, nullable = false)
    private String tzep;

    /**
     * Commodity group code (e.g., "Total" or "01".."12").
     * Part of the natural business key, therefore mandatory.
     */
    @Column(name = "mcrd081", length = 100, nullable = false)
    private String mcrd081;

    /**
     * Metric description from NBU (e.g., "txt" or "txten").
     */
    @Column(name = "description", length = 255)
    private String description;

    public NbuYearlyInflation(Integer inflationYear, BigDecimal ratePercent, String tzep, String mcrd081, String description) {
        this.inflationYear = inflationYear;
        this.ratePercent = ratePercent != null ? ratePercent.setScale(4, RoundingMode.HALF_EVEN) : BigDecimal.ZERO;
        this.tzep = tzep;
        this.mcrd081 = mcrd081;
        this.description = description;
    }

    /**
     * Factory method to convert an NBU annual DTO into a persistent entity.
     *
     * @throws IllegalStateException if the DTO carries an unparseable date or is
     *         missing the mandatory index type / commodity group, so that bad
     *         data fails loudly instead of being persisted under the wrong year.
     */
    public static NbuYearlyInflation fromDto(NbuYearlyInflationRecordDto dto) {
        Year year = dto.toYear();
        if (year == null) {
            throw new IllegalStateException(
                    "Unparseable NBU date '" + dto.dt() + "' - refusing to persist the record");
        }
        if (dto.tzep() == null || dto.tzep().isBlank()
                || dto.mcrd081() == null || dto.mcrd081().isBlank()) {
            throw new IllegalStateException(
                    "NBU annual record dated " + dto.dt() + " is missing index type or commodity group");
        }

        return new NbuYearlyInflation(
                year.getValue(),
                dto.toRatePercent(),
                dto.tzep(),
                dto.mcrd081(),
                dto.txt()
        );
    }
}