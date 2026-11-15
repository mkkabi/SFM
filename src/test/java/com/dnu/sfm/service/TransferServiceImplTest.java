package com.dnu.sfm.service;

import com.dnu.sfm.dto.TransferResultDto;
import com.dnu.sfm.exception.CurrencyMismatchException;
import com.dnu.sfm.exception.InactiveAccountException;
import com.dnu.sfm.model.Account;
import com.dnu.sfm.model.AccountStatus;
import com.dnu.sfm.model.CashAccount;
import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.DepositAccount;
import com.dnu.sfm.model.TransactionType;
import com.dnu.sfm.repository.AccountRepository;
import com.dnu.sfm.repository.TransactionRepository;
import com.dnu.sfm.service.impl.TransferServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the transfer rules: cash to cash, cash to deposit,
 * same currency only, and the DEBIT/CREDIT transaction pair recorded
 * in both accounts.
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionService transactionService;

    private TransferServiceImpl transferService;

    private CashAccount source;
    private CashAccount targetCash;
    private DepositAccount targetDeposit;

    @BeforeEach
    void setUp() {
        transferService = new TransferServiceImpl(accountRepository, transactionRepository, transactionService);

        source = new CashAccount();
        source.setId(1L);
        source.setName("Wallet");
        source.setBalance(new BigDecimal("1000.00"));
        source.setCurrency(Currency.USD);
        source.setStatus(AccountStatus.ACTIVE);
        source.setStartDate(LocalDate.now());

        targetCash = new CashAccount();
        targetCash.setId(2L);
        targetCash.setName("Savings Wallet");
        targetCash.setBalance(new BigDecimal("50.00"));
        targetCash.setCurrency(Currency.USD);
        targetCash.setStatus(AccountStatus.ACTIVE);
        targetCash.setStartDate(LocalDate.now());

        targetDeposit = new DepositAccount(
                "Term Deposit",
                new BigDecimal("100.00"),
                Currency.USD,
                new BigDecimal("5.5"),
                BigDecimal.ZERO,
                LocalDate.now().minusMonths(3),
                LocalDate.now().plusMonths(9)
        );
        targetDeposit.setId(3L);
        targetDeposit.setStatus(AccountStatus.ACTIVE);
    }

    @Test
    void transferBetweenCashAccountsUpdatesBalancesAndRecordsDebitAndCredit() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(targetCash));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        TransferResultDto result = transferService.transfer(1L, 2L, new BigDecimal("250.00"));

        assertThat(result.amount()).isEqualByComparingTo("250.00");
        assertThat(result.fromBalanceAfter()).isEqualByComparingTo("750.00");
        assertThat(result.toBalanceAfter()).isEqualByComparingTo("300.00");
        assertThat(source.getBalance()).isEqualByComparingTo("750.00");
        assertThat(targetCash.getBalance()).isEqualByComparingTo("300.00");

        // A transaction must be registered in BOTH accounts: debit on source, credit on target,
        // both legs tagged with the same transfer reference
        ArgumentCaptor<String> refCaptor = ArgumentCaptor.forClass(String.class);
        verify(transactionService).recordTransaction(
                eq(source), eq(TransactionType.DEBIT), eq(new BigDecimal("250.00")), refCaptor.capture());
        verify(transactionService).recordTransaction(
                eq(targetCash), eq(TransactionType.CREDIT), eq(new BigDecimal("250.00")), refCaptor.capture());

        assertThat(refCaptor.getAllValues()).hasSize(2);
        assertThat(refCaptor.getAllValues().get(0)).startsWith("TRF-");
        assertThat(refCaptor.getAllValues().get(1)).isEqualTo(refCaptor.getAllValues().get(0));
        assertThat(result.transferRef()).isEqualTo(refCaptor.getAllValues().get(0));
    }

    @Test
    void transferFromCashToDepositAccountIsAllowed() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findById(3L)).thenReturn(Optional.of(targetDeposit));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        TransferResultDto result = transferService.transfer(1L, 3L, new BigDecimal("100.00"));

        assertThat(result.toAccountId()).isEqualTo(3L);
        assertThat(source.getBalance()).isEqualByComparingTo("900.00");
        assertThat(targetDeposit.getBalance()).isEqualByComparingTo("200.00");

        verify(transactionService).recordTransaction(
                eq(source), eq(TransactionType.DEBIT), eq(new BigDecimal("100.00")), anyString());
        verify(transactionService).recordTransaction(
                eq(targetDeposit), eq(TransactionType.CREDIT), eq(new BigDecimal("100.00")), anyString());
    }

    @Test
    void transferToSameAccountIsRejected() {
        assertThatThrownBy(() -> transferService.transfer(1L, 1L, new BigDecimal("10.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source and target must be different");

        verify(transactionService, never())
                .recordTransaction(any(Account.class), any(TransactionType.class), any(BigDecimal.class), anyString());
    }

    @Test
    void transferFromDepositAccountIsRejected() {
        when(accountRepository.findById(3L)).thenReturn(Optional.of(targetDeposit));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(targetCash));

        assertThatThrownBy(() -> transferService.transfer(3L, 2L, new BigDecimal("10.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only from cash accounts");

        verify(transactionService, never())
                .recordTransaction(any(Account.class), any(TransactionType.class), any(BigDecimal.class), anyString());
    }

    @Test
    void transferBetweenDifferentCurrenciesIsRejected() {
        targetCash.setCurrency(Currency.UAH);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(targetCash));

        assertThatThrownBy(() -> transferService.transfer(1L, 2L, new BigDecimal("10.00")))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessageContaining("Currency mismatch");

        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionService, never())
                .recordTransaction(any(Account.class), any(TransactionType.class), any(BigDecimal.class), anyString());
    }

    @Test
    void transferWithInsufficientFundsIsRejected() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(targetCash));

        assertThatThrownBy(() -> transferService.transfer(1L, 2L, new BigDecimal("5000.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient funds");

        verify(transactionService, never())
                .recordTransaction(any(Account.class), any(TransactionType.class), any(BigDecimal.class), anyString());
    }

    @Test
    void transferToInactiveTargetIsRejected() {
        targetCash.setStatus(AccountStatus.FROZEN);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(targetCash));

        assertThatThrownBy(() -> transferService.transfer(1L, 2L, new BigDecimal("10.00")))
                .isInstanceOf(InactiveAccountException.class)
                .hasMessageContaining("ACTIVE");

        verify(transactionService, never())
                .recordTransaction(any(Account.class), any(TransactionType.class), any(BigDecimal.class), anyString());
    }

    @Test
    void transferWithNonPositiveAmountIsRejected() {
        assertThatThrownBy(() -> transferService.transfer(1L, 2L, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");

        assertThatThrownBy(() -> transferService.transfer(1L, 2L, new BigDecimal("-5.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");

        verify(transactionService, never())
                .recordTransaction(any(Account.class), any(TransactionType.class), any(BigDecimal.class), anyString());
    }

    @Test
    void transferRecordsExactlyOneDebitAndOneCreditTransaction() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(targetCash));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        TransferResultDto result = transferService.transfer(1L, 2L, new BigDecimal("40.00"));

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        ArgumentCaptor<TransactionType> typeCaptor = ArgumentCaptor.forClass(TransactionType.class);
        ArgumentCaptor<String> refCaptor = ArgumentCaptor.forClass(String.class);
        verify(transactionService, times(2))
                .recordTransaction(accountCaptor.capture(), typeCaptor.capture(),
                        eq(new BigDecimal("40.00")), refCaptor.capture());

        assertThat(typeCaptor.getAllValues()).containsExactly(TransactionType.DEBIT, TransactionType.CREDIT);
        assertThat(accountCaptor.getAllValues()).containsExactly(source, targetCash);

        // Both legs share one generated reference like TRF-3F9A2C1B8D4E
        assertThat(refCaptor.getAllValues()).hasSize(2);
        assertThat(refCaptor.getAllValues().get(0)).startsWith("TRF-").hasSize("TRF-".length() + 12);
        assertThat(refCaptor.getAllValues().get(1)).isEqualTo(refCaptor.getAllValues().get(0));
        assertThat(result.transferRef()).isEqualTo(refCaptor.getAllValues().get(0));
    }
}
