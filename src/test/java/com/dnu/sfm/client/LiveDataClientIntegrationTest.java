package com.dnu.sfm.client;

import com.dnu.sfm.dto.MonthlyInflationStatBureauRecordDto;
import com.dnu.sfm.dto.NbuYearlyInflationRecordDto;
import com.dnu.sfm.dto.NbuMonthlyInflationRecordDto;
import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.ExchangeRate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("live-api")
class LiveDataClientIntegrationTest {

    private NbuExchangeRateClient nbuClient;
    private StatBureauInflationClient statBureauClient;
    private NbuInflationClient nbuInflationClient;

    @BeforeEach
    void setUp() {
        // Plain builder without MockRestServiceServer.bindTo(...) -> connects to real internet
        RestClient.Builder builder = RestClient.builder();
        ObjectMapper objectMapper = new ObjectMapper();
        nbuClient = new NbuExchangeRateClient(builder);
        statBureauClient = new StatBureauInflationClient(builder, objectMapper);
        nbuInflationClient = new NbuInflationClient(builder);
    }

    @Test
    @DisplayName("Verify NBU live endpoint returns valid exchange rates for recent weekday")
    void testLiveNbuRates() {
        // Use a known recent past weekday to avoid weekend publication delays
        LocalDate testDate = LocalDate.now().minusDays(3);
        List<ExchangeRate> rates = nbuClient.getRatesForDate(testDate);

        // Assert that real data was received and parsed
        assertThat(rates).isNotNull().isNotEmpty();

        ExchangeRate usd = rates.stream()
                .filter(r -> "USD".equalsIgnoreCase(r.getCurrencyCode()))
                .findFirst()
                .orElse(null);

        assertThat(usd).isNotNull();
        assertThat(usd.getRate()).isGreaterThan(BigDecimal.valueOf(30)); // Realistic UAH/USD baseline
        System.out.printf(">> LIVE NBU SUCCESS: 1 USD = %s UAH on %s%n", usd.getRate(), usd.getExchangeDate());
    }

    @Test
    @DisplayName("verify NBU inflation Monthly data is not empty")
    void testLiveNbuInflationMonthly() {
        List<NbuMonthlyInflationRecordDto> nbuMonthlyInflationRecordDtos = nbuInflationClient.getUkraineMonthlyInflation(LocalDate.of(2023, 1, 1));
        assertThat(nbuMonthlyInflationRecordDtos).isNotNull().isNotEmpty();
        for (NbuMonthlyInflationRecordDto dto : nbuMonthlyInflationRecordDtos) {
            System.out.println(dto);
        }
    }


    @Test
    @DisplayName("verify NBU inflation Annual data is not empty")
    void testLiveNbuInflationYearly() {
        List<NbuYearlyInflationRecordDto> nbuYearlyInflationRecordDtos = nbuInflationClient.getUkraineAnnualInflation(LocalDate.of(2023, 1, 1));
        assertThat(nbuYearlyInflationRecordDtos).isNotNull().isNotEmpty();
        for (NbuYearlyInflationRecordDto dto : nbuYearlyInflationRecordDtos) {
            System.out.println(dto);
        }
    }


    @Test
    @DisplayName("Verify StatBureau live endpoint returns historical records for Ukraine")
    void testLiveStatBureauHistoryUAH() {
        List<MonthlyInflationStatBureauRecordDto> history = statBureauClient.getHistoricalInflation(Currency.UAH);

        assertThat(history).isNotNull().isNotEmpty();
        MonthlyInflationStatBureauRecordDto latest = history.getFirst();

        assertThat(latest.month()).isNotNull();
        assertThat(latest.ratePercent()).isNotNull();
        System.out.printf(">> LIVE STATBUREAU SUCCESS: Latest UAH Inflation for %s is %s%%%n",
                latest.month(), latest.ratePercent());

        for (MonthlyInflationStatBureauRecordDto record : history) {
            System.out.println(record);
        }
    }


    @Test
    @DisplayName("Verify StatBureau live endpoint returns historical records for Europe")
    void testLiveStatBureauHistoryEUR() {
        List<MonthlyInflationStatBureauRecordDto> history = statBureauClient.getHistoricalInflation(Currency.EUR);

        assertThat(history).isNotNull().isNotEmpty();
        MonthlyInflationStatBureauRecordDto latest = history.getFirst();

        assertThat(latest.month()).isNotNull();
        assertThat(latest.ratePercent()).isNotNull();
        System.out.printf(">> LIVE STATBUREAU SUCCESS: Latest EUR Inflation for %s is %s%%%n",
                latest.month(), latest.ratePercent());

        for (MonthlyInflationStatBureauRecordDto record : history) {
            System.out.println(record);
        }
    }

    @Test
    @DisplayName("Verify StatBureau live endpoint returns historical records for USA")
    void testLiveStatBureauHistoryUSA() {
        List<MonthlyInflationStatBureauRecordDto> history = statBureauClient.getHistoricalInflation(Currency.USD);

        assertThat(history).isNotNull().isNotEmpty();
        MonthlyInflationStatBureauRecordDto latest = history.getFirst();

        assertThat(latest.month()).isNotNull();
        assertThat(latest.ratePercent()).isNotNull();
        System.out.printf(">> LIVE STATBUREAU SUCCESS: Latest USD Inflation for %s is %s%%%n",
                latest.month(), latest.ratePercent());

        for (MonthlyInflationStatBureauRecordDto record : history) {
            System.out.println(record);
        }
    }
}