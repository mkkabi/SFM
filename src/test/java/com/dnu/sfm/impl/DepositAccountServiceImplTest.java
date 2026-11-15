package com.dnu.sfm.service.impl;

import com.dnu.sfm.exception.MaturityNotReachedException;
import com.dnu.sfm.exception.MaturityReachedException;
import com.dnu.sfm.model.AccountStatus;
import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.DepositAccount;
import com.dnu.sfm.repository.AccountRepository;
import com.dnu.sfm.repository.DepositAccountRepository;
import com.dnu.sfm.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepositAccountServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private DepositAccountRepository depositAccountRepository;

    @Mock
    private TransactionService transactionService;

    @Mock
    private DepositInterestCalculatorService depositInterestCalculatorService;

    @InjectMocks
    private DepositAccountServiceImpl service;

    private DepositAccount activeDeposit(LocalDate start, LocalDate maturity) {
        DepositAccount account = new DepositAccount("Term Deposit",
                new BigDecimal("1000.00"), Currency.UAH,
                new BigDecimal("10.00"), BigDecimal.ZERO, start, maturity);
        account.setId(1L);
        account.setStatus(AccountStatus.ACTIVE);
        return account;
    }

    @Test
    @DisplayName("Depositing on the maturity date is rejected")
    void depositOnMaturityDateIsRejected() {
        LocalDate today = LocalDate.now();
        DepositAccount account = activeDeposit(today.minusYears(1), today);
        when(depositAccountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.depositToDepositAccount(1L, new BigDecimal("100.00")))
                .isInstanceOf(MaturityReachedException.class)
                .hasMessageContaining("maturity date")
                .hasMessageContaining(today.toString());

        verify(depositAccountRepository, never()).save(any());
        verifyNoInteractions(transactionService);
    }

    @Test
    @DisplayName("Depositing after the maturity date is rejected")
    void depositAfterMaturityDateIsRejected() {
        LocalDate today = LocalDate.now();
        DepositAccount account = activeDeposit(today.minusYears(1), today.minusDays(1));
        when(depositAccountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.depositToDepositAccount(1L, new BigDecimal("100.00")))
                .isInstanceOf(MaturityReachedException.class)
                .hasMessageContaining("no longer allowed");

        verify(depositAccountRepository, never()).save(any());
        verifyNoInteractions(transactionService);
    }

    @Test
    @DisplayName("Depositing before the maturity date still succeeds")
    void depositBeforeMaturityStillSucceeds() {
        LocalDate today = LocalDate.now();
        DepositAccount account = activeDeposit(today.minusDays(30), today.plusYears(1));
        when(depositAccountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(depositAccountRepository.save(any(DepositAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DepositAccount saved = service.depositToDepositAccount(1L, new BigDecimal("100.00"));

        assertThat(saved.getBalance()).isEqualByComparingTo("1100.00");
        verify(transactionService).recordTransaction(eq(saved), any(), eq(new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("Withdrawal remains blocked before maturity (existing rule unchanged)")
    void withdrawBeforeMaturityIsStillRejected() {
        LocalDate today = LocalDate.now();
        DepositAccount account = activeDeposit(today.minusDays(30), today.plusYears(1));
        when(depositAccountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.withdrawFromDepositAccount(1L, new BigDecimal("100.00")))
                .isInstanceOf(MaturityNotReachedException.class);

        verify(depositAccountRepository, never()).save(any());
        verifyNoInteractions(transactionService);
    }
}
