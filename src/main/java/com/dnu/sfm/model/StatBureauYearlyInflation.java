package com.dnu.sfm.model;

import com.dnu.sfm.dto.YearlyInflationStatBureauRecordDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * Persistent record of one annual consumer price inflation measurement taken
 * from the StatBureau.org API for a single calendar year.
 * <p>
 * The application tracks exactly one economy per {@link Currency}
 * (UAH &rarr; ukraine, EUR &rarr; eurozone, USD &rarr; united-states) and
 * StatBureau answers with one annual rate per year, so the natural business
 * key is (inflation year, currency).
 * <p>
 * Note: {@code rate_percent} deliberately carries more integer headroom than
 * the monthly entity, because hyperinflation-era annual rates (e.g. UAH 1992
 * at +2730% or 1993 at well above +10000%) exceed what {@code decimal(7,4)}
 * could hold.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "statbureau_yearly_inflation",
        indexes = {
                @Index(name = "idx_statbureau_yearly_year", columnList = "inflation_year"),
                @Index(name = "idx_statbureau_yearly_currency_year", columnList = "currency, inflation_year")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_statbureau_yearly_year_currency",
                        columnNames = {"inflation_year", "currency"}
                )
        }
)
public class StatBureauYearlyInflation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Calendar year of the inflation record (e.g., 2019).
     */
    @Column(name = "inflation_year", nullable = false)
    private Integer inflationYear;

    /**
     * Application currency whose economy the value belongs to
     * (UAH &rarr; ukraine, EUR &rarr; eurozone, USD &rarr; united-states).
     * Part of the natural business key, therefore mandatory.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    /**
     * Annual inflation rate percentage (e.g., 4.0500 for +4.05% over the year).
     */
    @Column(name = "rate_percent", nullable = false, precision = 10, scale = 4)
    private BigDecimal ratePercent;

    public StatBureauYearlyInflation(Integer inflationYear, Currency currency, BigDecimal ratePercent) {
        this.inflationYear = inflationYear;
        this.currency = currency;
        this.ratePercent = ratePercent != null ? ratePercent.setScale(4, RoundingMode.HALF_EVEN) : BigDecimal.ZERO;
    }

    /**
     * Factory method to convert a StatBureau annual DTO into a persistent entity
     * for the given currency.
     *
     * @throws IllegalStateException if the DTO carries no year or the currency is
     *         missing, so that bad data fails loudly instead of being persisted
     *         under the wrong year or economy.
     */
    public static StatBureauYearlyInflation fromDto(YearlyInflationStatBureauRecordDto dto, Currency currency) {
        if (currency == null) {
            throw new IllegalStateException("StatBureau annual record cannot be persisted without a currency");
        }
        if (dto == null || dto.toYear() == null) {
            throw new IllegalStateException(
                    "Unparseable StatBureau annual record"
                            + (dto != null && dto.formattedRate() != null ? " (rate " + dto.formattedRate() + ")" : "")
                            + " - refusing to persist the record");
        }

        return new StatBureauYearlyInflation(
                dto.toYear(),
                currency,
                dto.toRatePercent()
        );
    }
}
