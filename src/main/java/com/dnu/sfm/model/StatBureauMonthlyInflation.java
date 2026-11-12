package com.dnu.sfm.model;

import com.dnu.sfm.dto.MonthlyInflationStatBureauRecordDto;
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
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Persistent record of one month-over-month consumer price inflation measurement
 * taken from the StatBureau.org API ("get-data-json" endpoint).
 * <p>
 * One StatBureau request answers with the full available monthly history of one
 * economy, and the application tracks exactly one economy per {@link Currency}
 * (UAH -> ukraine, EUR -> eurozone, USD -> united-states), so the natural
 * business key is (record month, currency).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "statbureau_monthly_inflation",
        indexes = {
                @Index(name = "idx_statbureau_monthly_date", columnList = "record_date"),
                @Index(name = "idx_statbureau_monthly_currency_date", columnList = "currency, record_date")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_statbureau_monthly_date_currency",
                        columnNames = {"record_date", "currency"}
                )
        }
)
public class StatBureauMonthlyInflation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * First day of the reporting month (e.g., 2019-12-01).
     */
    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    /**
     * Application currency whose economy the value belongs to
     * (UAH -> ukraine, EUR -> eurozone, USD -> united-states).
     * Part of the natural business key, therefore mandatory.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    /**
     * Month-over-month inflation rate percentage (e.g., -0.2000 for -0.20%).
     */
    @Column(name = "rate_percent", nullable = false, precision = 7, scale = 4)
    private BigDecimal ratePercent;

    public StatBureauMonthlyInflation(LocalDate recordDate, Currency currency, BigDecimal ratePercent) {
        this.recordDate = recordDate;
        this.currency = currency;
        this.ratePercent = ratePercent != null ? ratePercent.setScale(4, RoundingMode.HALF_EVEN) : BigDecimal.ZERO;
    }

    /**
     * Factory method to convert a StatBureau monthly DTO into a persistent entity
     * for the given currency.
     *
     * @throws IllegalStateException if the DTO carries an unparseable month, so
     *         that bad data fails loudly instead of being persisted under the
     *         wrong month.
     */
    public static StatBureauMonthlyInflation fromDto(MonthlyInflationStatBureauRecordDto dto, Currency currency) {
        if (currency == null) {
            throw new IllegalStateException("StatBureau record cannot be persisted without a currency");
        }
        if (dto == null || dto.month() == null) {
            throw new IllegalStateException(
                    "Unparseable StatBureau month"
                            + (dto != null && dto.formattedRate() != null ? " (rate " + dto.formattedRate() + ")" : "")
                            + " - refusing to persist the record");
        }

        YearMonth yearMonth = YearMonth.from(dto.month());
        return new StatBureauMonthlyInflation(
                yearMonth.atDay(1),
                currency,
                dto.ratePercent()
        );
    }
}
