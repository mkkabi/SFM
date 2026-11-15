package com.dnu.sfm.service.impl;

import com.dnu.sfm.exception.MaturityReachedException;
import com.dnu.sfm.model.AccountStatus;
import com.dnu.sfm.model.CashAccount;
import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.DepositAccount;
import com.dnu.sfm.model.TransactionType;
import com.dnu.sfm.repository.AccountRepository;
import com.dnu.sfm.repository.TransactionRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private TransferServiceImpl service;

    private CashAccount sourceWallet() {
        CashAccount wallet = new CashAccount();
        wallet.setId(1L);
        wallet.setName("Wallet");
        wallet.setCurrency(Currency.USD);
        wallet.setStatus(AccountStatus.ACTIVE);
        wallet.setBalance(new BigDecimal("500.00"));
        return wallet;
    }

    private DepositAccount targetDeposit(LocalDate maturity) {
        DepositAccount deposit = new DepositAccount("Term Deposit",
                new BigDecimal("1000.00"), Currency.USD,
                new BigDecimal("10.00"), BigDecimal.ZERO,
                LocalDate.now().minusYears(1), maturity);
        deposit.setId(2L);
        deposit.setStatus(AccountStatus.ACTIVE);
        return deposit;
    }

    @Test
    @DisplayName("Transferring into a deposit that reached maturity is rejected")
    void transferToMaturedDepositIsRejected() {
        CashAccount wallet = sourceWallet();
        DepositAccount deposit = targetDeposit(LocalDate.now().minusDays(1));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(deposit));

        assertThatThrownBy(() -> service.transfer(1L, 2L, new BigDecimal("50.00")))
                .isInstanceOf(MaturityReachedException.class)
                .hasMessageContaining("maturity date");

        verifyNoInteractions(transactionService);
    }

    @Test
    @DisplayName("Transferring into a deposit on its maturity date is rejected (boundary)")
    void transferToDepositOnMaturityDateIsRejected() {
        CashAccount wallet = sourceWallet();
        DepositAccount deposit = targetDeposit(LocalDate.now());
        when(accountRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(deposit));

        assertThatThrownBy(() -> service.transfer(1L, 2L, new BigDecimal("50.00")))
                .isInstanceOf(MaturityReachedException.class);

        verifyNoInteractions(transactionService);
    }

    @Test
    @DisplayName("Transferring into a deposit that has not matured yet still succeeds")
    void transferToNotMaturedDepositStillSucceeds() {
        CashAccount wallet = sourceWallet();
        DepositAccount deposit = targetDeposit(LocalDate.now().plusYears(1));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(deposit));
        when(accountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.transfer(1L, 2L, new BigDecimal("50.00"));

        assertThat(wallet.getBalance()).isEqualByComparingTo("450.00");
        assertThat(deposit.getBalance()).isEqualByComparingTo("1050.00");
        verify(transactionService).recordTransaction(eq(wallet), eq(TransactionType.DEBIT),
                eq(new BigDecimal("50.00")), anyString());
        verify(transactionService).recordTransaction(eq(deposit), eq(TransactionType.CREDIT),
                eq(new BigDecimal("50.00")), anyString());
    }
}
