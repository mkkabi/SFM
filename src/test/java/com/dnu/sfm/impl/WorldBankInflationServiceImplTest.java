package com.dnu.sfm.impl;

import com.dnu.sfm.client.WorldBankInflationClient;
import com.dnu.sfm.dto.WorldBankInflationRecordDto;
import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.WorldBankInflation;
import com.dnu.sfm.repository.WorldBankInflationRepository;
import com.dnu.sfm.service.impl.WorldBankInflationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies the single-economy fetch and the per-currency headline lookup used
 * by the EUR-only World Bank chart: only the requested currency's economy is
 * queried remotely, rows without a published value are skipped, and the
 * all-currencies fetch still fans out over every tracked currency.
 */
@ExtendWith(MockitoExtension.class)
class WorldBankInflationServiceImplTest {

    @Mock
    private WorldBankInflationRepository repository;

    @Mock
    private WorldBankInflationClient client;

    @InjectMocks
    private WorldBankInflationServiceImpl service;

    private WorldBankInflationRecordDto row(String date, String value) {
        return new WorldBankInflationRecordDto(
                new WorldBankInflationRecordDto.IndicatorRef("FP.CPI.TOTL.ZG", "Inflation, consumer prices (annual %)"),
                new WorldBankInflationRecordDto.CountryRef("XC", "Euro area"),
                "", date,
                value != null ? new BigDecimal(value) : null,
                null, null, null);
    }

    @Test
    @DisplayName("Single-economy fetch converts published rows and skips the null values of unpublished years")
    void singleCurrencyFetchSkipsNullValues() {
        when(client.fetchAnnualInflationRecords(Currency.EUR, 2024)).thenReturn(List.of(
                row("2023", "6.4"),
                row("2024", null) // not published yet
        ));

        List<WorldBankInflation> entities = service.fetchFromWorldBank(Currency.EUR, 2024);

        assertThat(entities).hasSize(1);
        WorldBankInflation entity = entities.get(0);
        assertThat(entity.getInflationYear()).isEqualTo(2023);
        assertThat(entity.getCurrency()).isEqualTo(Currency.EUR);
        assertThat(entity.getCountryCode()).isEqualTo("XC");
        assertThat(entity.getCountryName()).isEqualTo("Euro area");
        assertThat(entity.getRatePercent()).isEqualByComparingTo("6.4000");

        // Exactly one remote call (EUR above): no other economy was queried
        verify(client, times(1)).fetchAnnualInflationRecords(any(Currency.class), anyInt());
    }

    @Test
    @DisplayName("A currency/year the World Bank has no data for yields an empty fetch result")
    void emptyRemoteAnswerYieldsEmptyResult() {
        when(client.fetchAnnualInflationRecords(eq(Currency.EUR), eq(2026))).thenReturn(List.of());

        List<WorldBankInflation> entities = service.fetchFromWorldBank(Currency.EUR, 2026);

        assertThat(entities).isEmpty();
        verify(client, times(1)).fetchAnnualInflationRecords(Currency.EUR, 2026);
    }

    @Test
    @DisplayName("The all-currencies fetch fans out over every tracked currency")
    void allCurrenciesFetchFansOut() {
        for (Currency currency : Currency.values()) {
            when(client.fetchAnnualInflationRecords(currency, 2024)).thenReturn(List.of());
        }

        List<WorldBankInflation> entities = service.fetchFromWorldBank(2024);

        assertThat(entities).isEmpty();
        verify(client).fetchAnnualInflationRecords(Currency.UAH, 2024);
        verify(client).fetchAnnualInflationRecords(Currency.EUR, 2024);
        verify(client).fetchAnnualInflationRecords(Currency.USD, 2024);
    }

    @Test
    @DisplayName("The per-currency headline lookup reads the stored row of that economy only")
    void perCurrencyHeadlineLookup() {
        WorldBankInflation euroRow = new WorldBankInflation(2024, Currency.EUR, "XC", "Euro area",
                "FP.CPI.TOTL.ZG", new BigDecimal("2.4"));
        when(repository.findByInflationYearAndCurrency(2024, Currency.EUR))
                .thenReturn(Optional.of(euroRow));

        Optional<WorldBankInflation> stored = service.findHeadlineAnnualCpi(Currency.EUR, 2024);

        assertThat(stored).isPresent();
        assertThat(stored.get().getCurrency()).isEqualTo(Currency.EUR);
        assertThat(stored.get().getRatePercent()).isEqualByComparingTo("2.4000");
        verifyNoInteractions(client);
    }
}
