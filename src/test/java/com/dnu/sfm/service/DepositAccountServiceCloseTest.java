package com.dnu.sfm.service;

import com.dnu.sfm.dto.DepositCloseResultDto;
import com.dnu.sfm.dto.DepositSettlementDto;
import com.dnu.sfm.exception.CurrencyMismatchException;
import com.dnu.sfm.exception.InactiveAccountException;
import com.dnu.sfm.exception.MaturityNotReachedException;
import com.dnu.sfm.model.Account;
import com.dnu.sfm.model.AccountStatus;
import com.dnu.sfm.model.CashAccount;
import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.DepositAccount;
import com.dnu.sfm.model.TransactionType;
import com.dnu.sfm.repository.AccountRepository;
import com.dnu.sfm.repository.DepositAccountRepository;
import com.dnu.sfm.service.impl.DepositAccountServiceImpl;
import com.dnu.sfm.service.impl.DepositInterestCalculatorService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for closing a matured deposit and withdrawing the payout
 * (principal + net interest) by transferring it to a user-selected
 * cash account.
 */
@ExtendWith(MockitoExtension.class)
class DepositAccountServiceCloseTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private DepositAccountRepository depositAccountRepository;

    @Mock
    private TransactionService transactionService;

    @Mock
    private DepositInterestCalculatorService depositInterestCalculatorService;

    private DepositAccountServiceImpl depositAccountService;

    private DepositAccount maturedDeposit;
    private CashAccount target;

    @BeforeEach
    void setUp() {
        depositAccountService = new DepositAccountServiceImpl(
                accountRepository, depositAccountRepository, transactionService, depositInterestCalculatorService);

        maturedDeposit = new DepositAccount(
                "Term Deposit",
                new BigDecimal("1000.00"),
                Currency.USD,
                new BigDecimal("5.5000"),
                new BigDecimal("5.0000"),
                LocalDate.now().minusMonths(12),
                LocalDate.now().minusDays(1) // matured yesterday
        );
        maturedDeposit.setId(1L);
        maturedDeposit.setStatus(AccountStatus.ACTIVE);

        target = new CashAccount();
        target.setId(2L);
        target.setName("Wallet");
        target.setBalance(new BigDecimal("50.00"));
        target.setCurrency(Currency.USD);
        target.setStatus(AccountStatus.ACTIVE);
        target.setStartDate(LocalDate.now());
    }

    private static DepositSettlementDto settlement(BigDecimal netInterest) {
        return new DepositSettlementDto(
                1L, "Term Deposit", "USD",
                new BigDecimal("5.5000"), new BigDecimal("5.00"),
                LocalDate.now().minusMonths(12), LocalDate.now().minusDays(1),
                365, new BigDecimal("1000.00"), new BigDecimal("55.00"),
                new BigDecimal("2.75"), netInterest, netInterest,
                List.of()
        );
    }

    @Test
    void closeBeforeMaturityIsRejected() {
        maturedDeposit.setMaturityDate(LocalDate.now().plusDays(1));
        when(depositAccountRepository.findById(1L)).thenReturn(Optional.of(maturedDeposit));

        assertThatThrownBy(() -> depositAccountService.closeDepositAccountAndTransfer(1L, 2L))
                .isInstanceOf(MaturityNotReachedException.class)
                .hasMessageContaining("maturity date");

        verifyNoInteractions(transactionService);
    }

    @Test
    void closeAfterMaturityTransfersPayoutAndClosesDeposit() {
        when(depositAccountRepository.findById(1L)).thenReturn(Optional.of(maturedDeposit));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(target));
        when(depositInterestCalculatorService.calculateSettlement(1L))
                .thenReturn(settlement(new BigDecimal("27.25")));
        when(depositAccountRepository.save(any(DepositAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        DepositCloseResultDto result = depositAccountService.closeDepositAccountAndTransfer(1L, 2L);

        // Payout = principal + net interest
        assertThat(result.principal()).isEqualByComparingTo("1000.00");
        assertThat(result.netInterest()).isEqualByComparingTo("27.25");
        assertThat(result.payout()).isEqualByComparingTo("1027.25");

        // Deposit is emptied and closed
        assertThat(maturedDeposit.getBalance()).isEqualByComparingTo("0.00");
        assertThat(maturedDeposit.getStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(maturedDeposit.getAccruedInterest()).isEqualByComparingTo("0");

        // Target received the payout
        assertThat(target.getBalance()).isEqualByComparingTo("1077.25");

        // Transactions registered in BOTH accounts: debit on deposit, credit on target,
        // both legs tagged with the same transfer reference
        ArgumentCaptor<String> refCaptor = ArgumentCaptor.forClass(String.class);
        verify(transactionService).recordTransaction(
                eq(maturedDeposit), eq(TransactionType.DEBIT), eq(new BigDecimal("1027.25")), refCaptor.capture());
        verify(transactionService).recordTransaction(
                eq(target), eq(TransactionType.CREDIT), eq(new BigDecimal("1027.25")), refCaptor.capture());

        assertThat(refCaptor.getAllValues()).hasSize(2);
        assertThat(refCaptor.getAllValues().get(0)).startsWith("TRF-");
        assertThat(refCaptor.getAllValues().get(1)).isEqualTo(refCaptor.getAllValues().get(0));
    }

    @Test
    void closeToNonCashTargetIsRejected() {
        DepositAccount anotherDeposit = new DepositAccount(
                "Other Deposit", BigDecimal.ZERO, Currency.USD,
                BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDate.now(), LocalDate.now().plusMonths(6));
        anotherDeposit.setId(3L);
        anotherDeposit.setStatus(AccountStatus.ACTIVE);

        when(depositAccountRepository.findById(1L)).thenReturn(Optional.of(maturedDeposit));
        when(accountRepository.findById(3L)).thenReturn(Optional.of(anotherDeposit));

        assertThatThrownBy(() -> depositAccountService.closeDepositAccountAndTransfer(1L, 3L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cash account");

        verify(transactionService, never())
                .recordTransaction(any(Account.class), any(TransactionType.class), any(BigDecimal.class), anyString());
    }

    @Test
    void closeToDifferentCurrencyTargetIsRejected() {
        target.setCurrency(Currency.EUR);
        when(depositAccountRepository.findById(1L)).thenReturn(Optional.of(maturedDeposit));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> depositAccountService.closeDepositAccountAndTransfer(1L, 2L))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessageContaining("Currency mismatch");

        verify(transactionService, never())
                .recordTransaction(any(Account.class), any(TransactionType.class), any(BigDecimal.class), anyString());
    }

    @Test
    void closeToInactiveTargetIsRejected() {
        target.setStatus(AccountStatus.FROZEN);
        when(depositAccountRepository.findById(1L)).thenReturn(Optional.of(maturedDeposit));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> depositAccountService.closeDepositAccountAndTransfer(1L, 2L))
                .isInstanceOf(InactiveAccountException.class)
                .hasMessageContaining("ACTIVE");

        verify(transactionService, never())
                .recordTransaction(any(Account.class), any(TransactionType.class), any(BigDecimal.class), anyString());
    }

    @Test
    void closeIntoItselfIsRejected() {
        when(depositAccountRepository.findById(1L)).thenReturn(Optional.of(maturedDeposit));

        assertThatThrownBy(() -> depositAccountService.closeDepositAccountAndTransfer(1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("itself");

        verify(transactionService, never())
                .recordTransaction(any(Account.class), any(TransactionType.class), any(BigDecimal.class), anyString());
    }

    @Test
    void closeAlreadyClosedDepositIsRejected() {
        maturedDeposit.setStatus(AccountStatus.CLOSED);
        when(depositAccountRepository.findById(1L)).thenReturn(Optional.of(maturedDeposit));

        assertThatThrownBy(() -> depositAccountService.closeDepositAccountAndTransfer(1L, 2L))
                .isInstanceOf(InactiveAccountException.class)
                .hasMessageContaining("ACTIVE");

        verifyNoInteractions(transactionService);
    }
}
