package com.dnu.sfm.model;

import com.dnu.sfm.dto.WorldBankInflationRecordDto;
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
import java.time.Year;

/**
 * Persistent record of one annual consumer price inflation measurement taken
 * from the World Bank Open Data API (indicator "FP.CPI.TOTL.ZG",
 * "Inflation, consumer prices (annual %)").
 * <p>
 * The World Bank publishes one value per economy and calendar year, and the
 * application tracks exactly one economy per {@link Currency} (UAH -> Ukraine,
 * EUR -> Euro Area, USD -> United States), so the natural business key is
 * (inflation year, currency).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "worldbank_inflation",
        indexes = {
                @Index(name = "idx_worldbank_year", columnList = "inflation_year")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_worldbank_year_currency",
                        columnNames = {"inflation_year", "currency"}
                )
        }
)
public class WorldBankInflation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Calendar year of the inflation record (e.g., 2024).
     */
    @Column(name = "inflation_year", nullable = false)
    private Integer inflationYear;

    /**
     * Application currency whose economy the value belongs to
     * (UAH -> Ukraine, EUR -> Euro Area, USD -> United States).
     * Part of the natural business key, therefore mandatory.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    /**
     * World Bank economy code the value was queried for
     * (e.g., "UA", "XC" for the Euro Area, "US").
     */
    @Column(name = "country_code", length = 10, nullable = false)
    private String countryCode;

    /**
     * Economy name as reported by the World Bank
     * (e.g., "Ukraine", "Euro area", "United States").
     */
    @Column(name = "country_name", length = 100)
    private String countryName;

    /**
     * World Bank indicator the value was taken from (e.g., "FP.CPI.TOTL.ZG").
     */
    @Column(name = "indicator_code", length = 50)
    private String indicatorCode;

    /**
     * Annual inflation rate percentage (e.g., 9.5814 for +9.58%).
     */
    @Column(name = "rate_percent", nullable = false, precision = 7, scale = 4)
    private BigDecimal ratePercent;

    public WorldBankInflation(Integer inflationYear, Currency currency, String countryCode,
                              String countryName, String indicatorCode, BigDecimal ratePercent) {
        this.inflationYear = inflationYear;
        this.currency = currency;
        this.countryCode = countryCode;
        this.countryName = countryName;
        this.indicatorCode = indicatorCode;
        this.ratePercent = ratePercent != null ? ratePercent.setScale(4, RoundingMode.HALF_EVEN) : BigDecimal.ZERO;
    }

    /**
     * Factory method to convert a World Bank API row into a persistent entity
     * for the given currency.
     *
     * @throws IllegalStateException if the row carries no value, no country code
     *         or an unparseable date, so that bad data fails loudly instead of
     *         being persisted under the wrong year.
     */
    public static WorldBankInflation fromDto(WorldBankInflationRecordDto dto, Currency currency) {
        if (currency == null) {
            throw new IllegalStateException("World Bank record cannot be persisted without a currency");
        }
        if (dto == null || !dto.hasValue()) {
            throw new IllegalStateException(
                    "World Bank returned no inflation value for " + currency
                            + (dto != null && dto.date() != null ? " (" + dto.date() + ")" : "")
                            + " - refusing to persist the record");
        }
        Year year = dto.toYear();
        if (year == null) {
            throw new IllegalStateException(
                    "Unparseable World Bank date '" + dto.date() + "' - refusing to persist the record");
        }
        if (dto.country() == null || dto.country().id() == null || dto.country().id().isBlank()) {
            throw new IllegalStateException(
                    "World Bank record for " + currency + " (" + dto.date() + ") is missing the country code");
        }

        return new WorldBankInflation(
                year.getValue(),
                currency,
                dto.country().id(),
                dto.country().value(),
                dto.indicator() != null ? dto.indicator().id() : null,
                dto.toRatePercent()
        );
    }
}
