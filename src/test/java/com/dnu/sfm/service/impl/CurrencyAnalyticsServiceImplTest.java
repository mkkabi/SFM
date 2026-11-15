package com.dnu.sfm.service.impl;

import com.dnu.sfm.dto.AccountCurrencyComparisonDto;
import com.dnu.sfm.dto.CurrencyOutcomeDto;
import com.dnu.sfm.model.Account;
import com.dnu.sfm.model.AccountStatus;
import com.dnu.sfm.model.CashAccount;
import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.DepositAccount;
import com.dnu.sfm.model.Transaction;
import com.dnu.sfm.model.TransactionType;
import com.dnu.sfm.repository.AccountRepository;
import com.dnu.sfm.repository.TransactionRepository;
import com.dnu.sfm.service.ExchangeRateProviderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyAnalyticsServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ExchangeRateProviderService rateProviderService;

    @InjectMocks
    private CurrencyAnalyticsServiceImpl service;

    private static final LocalDate START = LocalDate.of(2025, 1, 10);
    private static final LocalDate MATURITY = LocalDate.of(2026, 1, 10);

    private DepositAccount closedUahDeposit() {
        DepositAccount account = new DepositAccount("Term Deposit",
                new BigDecimal("1000.00"), Currency.UAH,
                new BigDecimal("10.00"), BigDecimal.ZERO, START, MATURITY);
        account.setId(5L);
        account.setStatus(AccountStatus.CLOSED);
        account.setBalance(BigDecimal.ZERO); // closing empties the balance
        return account;
    }

    /**
     * Initial principal: 1000 UAH, converted at the transaction-date rates
     * (USD 41.0000, EUR 45.0000) into 24.39 USD / 22.22 EUR equivalents.
     */
    private Transaction principalTransaction(Account account) {
        return new Transaction(account, TransactionType.DEPOSIT,
                new BigDecimal("1000.00"), Currency.UAH,
                new BigDecimal("1000.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("24.39"),
                new BigDecimal("22.22"),
                new BigDecimal("41.0000"), new BigDecimal("45.0000"),
                LocalDateTime.of(2025, 1, 10, 9, 0));
    }

    /**
     * Cash deposit of 100.00 USD at transaction-date rates (USD 41.0000,
     * EUR 45.0000): 4100.00 UAH / 91.11 EUR equivalents.
     */
    private Transaction usdCashTransaction(Account account) {
        return new Transaction(account, TransactionType.DEPOSIT,
                new BigDecimal("100.00"), Currency.USD,
                new BigDecimal("100.00"),
                new BigDecimal("4100.00"),
                new BigDecimal("100.00"),
                new BigDecimal("91.11"),
                new BigDecimal("41.0000"), new BigDecimal("45.0000"),
                LocalDateTime.of(2025, 1, 10, 9, 0));
    }

    /**
     * Close-deposit payout: principal 1000.00 + net interest 100.00 = 1100.00
     * UAH, recorded as a DEBIT leg carrying the transfer reference.
     */
    private Transaction payoutTransaction(Account account) {
        Transaction payout = new Transaction(account, TransactionType.DEBIT,
                new BigDecimal("1100.00"), Currency.UAH,
                new BigDecimal("0.00"),
                new BigDecimal("1100.00"),
                new BigDecimal("26.19"),
                new BigDecimal("23.91"),
                new BigDecimal("42.0000"), new BigDecimal("46.0000"),
                LocalDateTime.of(2026, 1, 15, 12, 0));
        payout.setTransferRef("payout-ref-1");
        return payout;
    }

    @Test
    @DisplayName("Closed deposit: actual side is the close payout and the payout leg is excluded from the replay")
    void closedDepositComparisonUsesPayoutAsActual() {
        DepositAccount account = closedUahDeposit();
        // Repository returns transactions newest-first: payout (2026) before principal (2025)
        when(transactionRepository.findByAccountIdOrderByTimestampDescIdDesc(5L))
                .thenReturn(List.of(payoutTransaction(account), principalTransaction(account)));
        when(rateProviderService.getEffectiveRatesForDate(any()))
                .thenReturn(Map.of("USD", new BigDecimal("42.0000"), "EUR", new BigDecimal("46.0000")));
        when(accountRepository.findById(5L)).thenReturn(Optional.of(account));

        AccountCurrencyComparisonDto dto = service.compareCurrencyOutcomes(5L);

        // The payout (principal + net interest), not the zeroed balance, is the actual result
        assertThat(dto.accountClosed()).isTrue();
        assertThat(dto.actualBalance()).isEqualByComparingTo("1100.00");
        assertThat(dto.actualValueInUahToday()).isEqualByComparingTo("1100.00");

        // The account-currency row coincides with the actual (zero difference)
        CurrencyOutcomeDto uah = outcome(dto, "UAH");
        assertThat(uah.nominalBalance()).isEqualByComparingTo("1100.00");
        assertThat(uah.valueInBaseCurrencyToday()).isEqualByComparingTo("1100.00");
        assertThat(uah.differenceVersusActualUah()).isEqualByComparingTo("0.00");

        // Jars hold the committed principal only: 24.39 USD x 42 = 1024.38 UAH (worse),
        // 22.22 EUR x 46 = 1022.12 UAH (worse). No negative simulated balances.
        CurrencyOutcomeDto usd = outcome(dto, "USD");
        assertThat(usd.nominalBalance()).isEqualByComparingTo("24.39");
        assertThat(usd.valueInBaseCurrencyToday()).isEqualByComparingTo("1024.38");
        assertThat(usd.differenceVersusActualUah()).isEqualByComparingTo("-75.62");
        assertThat(usd.percentageDifferenceVersusActual()).isEqualByComparingTo("-6.87");

        CurrencyOutcomeDto eur = outcome(dto, "EUR");
        assertThat(eur.nominalBalance()).isEqualByComparingTo("22.22");
        assertThat(eur.valueInBaseCurrencyToday()).isEqualByComparingTo("1022.12");
        assertThat(eur.differenceVersusActualUah()).isEqualByComparingTo("-77.88");
        assertThat(eur.percentageDifferenceVersusActual()).isEqualByComparingTo("-7.08");

        assertThat(dto.alternatives())
                .allSatisfy(alt -> assertThat(alt.nominalBalance()).isPositive());

        assertThat(dto.recommendationVerdict())
                .contains("Holding UAH was the optimal strategy");
    }

    @Test
    @DisplayName("Closed deposit in USD: account-currency row is pinned to the payout and valued at today's rate")
    void closedUsdDepositComparisonUsesPayoutValuedAtTodaysRate() {
        DepositAccount account = closedUahDeposit();
        account.setCurrency(Currency.USD);
        account.setId(6L);
        // Payout and principal legs stay UAH-converted as recorded; the account
        // currency only changes the valuation side of the comparison
        when(transactionRepository.findByAccountIdOrderByTimestampDescIdDesc(6L))
                .thenReturn(List.of(payoutTransaction(account), principalTransaction(account)));
        when(rateProviderService.getEffectiveRatesForDate(any()))
                .thenReturn(Map.of("USD", new BigDecimal("42.0000"), "EUR", new BigDecimal("46.0000")));
        when(accountRepository.findById(6L)).thenReturn(Optional.of(account));

        AccountCurrencyComparisonDto dto = service.compareCurrencyOutcomes(6L);

        assertThat(dto.accountClosed()).isTrue();
        assertThat(dto.actualBalance()).isEqualByComparingTo("1100.00");
        // Payout is USD-denominated here: 1100.00 x 42 = 46200.00 UAH today
        assertThat(dto.actualValueInUahToday()).isEqualByComparingTo("46200.00");
        assertThat(outcome(dto, "USD").differenceVersusActualUah()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Open account behaviour is unchanged: actual side stays the balance and native row has zero difference")
    void openAccountComparisonIsUnchanged() {
        CashAccount account = new CashAccount();
        account.setId(7L);
        account.setName("Wallet");
        account.setCurrency(Currency.USD);
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(new BigDecimal("100.00"));

        when(transactionRepository.findByAccountIdOrderByTimestampDescIdDesc(7L))
                .thenReturn(List.of(usdCashTransaction(account)));
        when(rateProviderService.getEffectiveRatesForDate(any()))
                .thenReturn(Map.of("USD", new BigDecimal("41.0000"), "EUR", new BigDecimal("45.0000")));
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account));

        AccountCurrencyComparisonDto dto = service.compareCurrencyOutcomes(7L);

        assertThat(dto.accountClosed()).isFalse();
        assertThat(dto.actualBalance()).isEqualByComparingTo("100.00");
        assertThat(dto.actualValueInUahToday()).isEqualByComparingTo("4100.00");
        assertThat(outcome(dto, "USD").differenceVersusActualUah()).isEqualByComparingTo("0.00");
        // EUR jar: 91.11 x 45 = 4099.95 UAH - slightly worse than the USD strategy
        assertThat(outcome(dto, "EUR").differenceVersusActualUah()).isEqualByComparingTo("-0.05");

        assertThat(dto.recommendationVerdict())
                .contains("Holding USD was the optimal strategy");
    }

    @Test
    @DisplayName("Closed deposit without a payout leg (anomalous ledger) falls back to the balance-based analysis")
    void closedDepositWithoutPayoutLegFallsBackToBalance() {
        DepositAccount account = closedUahDeposit();
        when(transactionRepository.findByAccountIdOrderByTimestampDescIdDesc(5L))
                .thenReturn(List.of(principalTransaction(account)));
        when(rateProviderService.getEffectiveRatesForDate(any()))
                .thenReturn(Map.of("USD", new BigDecimal("42.0000"), "EUR", new BigDecimal("46.0000")));
        when(accountRepository.findById(5L)).thenReturn(Optional.of(account));

        AccountCurrencyComparisonDto dto = service.compareCurrencyOutcomes(5L);

        assertThat(dto.accountClosed()).isFalse();
        assertThat(dto.actualBalance()).isEqualByComparingTo("0.00");
        assertThat(dto.actualValueInUahToday()).isEqualByComparingTo("0.00");
    }

    private static CurrencyOutcomeDto outcome(AccountCurrencyComparisonDto dto, String currency) {
        return dto.alternatives().stream()
                .filter(o -> o.currencyCode().equals(currency))
                .findFirst()
                .orElseThrow();
    }
}
