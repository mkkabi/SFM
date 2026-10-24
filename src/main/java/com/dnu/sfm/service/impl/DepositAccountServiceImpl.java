package com.dnu.sfm.service.impl;

import com.dnu.sfm.exception.AccountNotFoundException;
import com.dnu.sfm.model.DepositAccount;
import com.dnu.sfm.repository.AccountRepository;
import com.dnu.sfm.service.DepositAccountService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional
public class DepositAccountServiceImpl implements DepositAccountService {

    private final AccountRepository accountRepository;

    @Override
    public DepositAccount createDepositAccount(String accountNumber, String name, BigDecimal initialDeposit, String currency, BigDecimal interestRate, LocalDate startDate, LocalDate maturityDate, boolean earlyWithdrawalAllowed, BigDecimal penaltyRate) {
        //TODO
        // Validation and creation of DepositAccount
        return null;
    }

    @Override
    public void accrueDailyInterest(String accountNumber) {
        DepositAccount account = (DepositAccount) accountRepository.findByAccountNumberForUpdate(accountNumber)
                .filter(a -> a instanceof DepositAccount)
                .orElseThrow(() -> new AccountNotFoundException("Deposit account not found: " + accountNumber));

        account.accrueDailyInterest();
        accountRepository.save(account);
    }

    @Override
    public void capitalizeInterest(String accountNumber) {
        DepositAccount account = (DepositAccount) accountRepository.findByAccountNumberForUpdate(accountNumber)
                .filter(a -> a instanceof DepositAccount)
                .orElseThrow(() -> new AccountNotFoundException("Deposit account not found: " + accountNumber));

        account.capitalizeInterest();
        accountRepository.save(account);
    }
}