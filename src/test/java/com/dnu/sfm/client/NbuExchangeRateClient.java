package com.dnu.sfm.client;

import com.dnu.sfm.model.ExchangeRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class NbuExchangeRateClientLiveIT {

    private NbuExchangeRateClient client;

    @BeforeEach
    void setUp() {
        // Build a real RestClient with Spring's default Jackson configuration
        // to ensure Java 8 date/time (JSR310) module and @JsonFormat are active.
        MappingJackson2HttpMessageConverter jacksonConverter =
                new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json().build());

        RestClient.Builder restClientBuilder = RestClient.builder()
                .messageConverters(converters -> converters.add(jacksonConverter));

        client = new NbuExchangeRateClient(restClientBuilder);
    }

    @Test
    @DisplayName("Verify live NBU data against immutable historical rates (2024-01-15)")
    void getRatesForDate_historicalFixedDate_returnsExactValues() {
        LocalDate historicalDate = LocalDate.of(2024, 1, 15);

        List<ExchangeRate> rates = client.getRatesForDate(historicalDate);

        assertThat(rates)
                .as("NBU response should not be null or empty")
                .isNotNull()
                .isNotEmpty();

        // 1. Verify USD (ISO 4217 numeric 840, alphabetic USD)
        Optional<ExchangeRate> usdRate = rates.stream()
                .filter(r -> "USD".equalsIgnoreCase(r.getCurrencyCode()))
                .findFirst();

        assertThat(usdRate).isPresent();
        assertThat(usdRate.get().getNumericCode()).isEqualTo(840);
        // NBU official rate for USD on 2024-01-15 was 37.8386 UAH
        assertThat(usdRate.get().getRate()).isEqualByComparingTo(new BigDecimal("37.8386"));
        assertThat(usdRate.get().getExchangeDate()).isEqualTo(historicalDate);

        // 2. Verify EUR (ISO 4217 numeric 978, alphabetic EUR)
        Optional<ExchangeRate> eurRate = rates.stream()
                .filter(r -> "EUR".equalsIgnoreCase(r.getCurrencyCode()))
                .findFirst();

        assertThat(eurRate).isPresent();
        assertThat(eurRate.get().getNumericCode()).isEqualTo(978);
        // NBU official rate for EUR on 2024-01-15 was 41.3879 UAH
        assertThat(eurRate.get().getRate()).isEqualByComparingTo(new BigDecimal("41.3879"));
        assertThat(eurRate.get().getExchangeDate()).isEqualTo(historicalDate);
    }

    @Test
    @DisplayName("Verify live NBU contract and financial domain invariants for a recent business day")
    void getRatesForDate_recentBusinessDay_returnsValidRates() {
        // Pick the most recent weekday to ensure published bank rates
        LocalDate queryDate = getMostRecentWeekday();

        List<ExchangeRate> rates = client.getRatesForDate(queryDate);

        assertThat(rates).isNotNull().isNotEmpty();

        // Financial invariant assertions
        for (ExchangeRate rate : rates) {
            assertThat(rate.getCurrencyCode())
                    .as("Currency code must be defined")
                    .isNotBlank();

            assertThat(rate.getRate())
                    .as("Exchange rate for %s must be positive", rate.getCurrencyCode())
                    .isNotNull()
                    .isGreaterThan(BigDecimal.ZERO);

            assertThat(rate.getNumericCode())
                    .as("Numeric currency code must be positive")
                    .isNotNull()
                    .isPositive();
        }

        // Verify required application currencies are always present in live response
        List<String> currencyCodes = rates.stream()
                .map(ExchangeRate::getCurrencyCode)
                .toList();

        assertThat(currencyCodes).contains("USD", "EUR");
    }

    private LocalDate getMostRecentWeekday() {
        LocalDate date = LocalDate.now();
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return date.minusDays(1);
        } else if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return date.minusDays(2);
        }
        return date;
    }
}