package com.dnu.sfm.service.impl;

import com.dnu.sfm.model.AccountStatus;
import com.dnu.sfm.model.CashAccount;
import com.dnu.sfm.repository.AccountRepository;
import com.dnu.sfm.service.CashAccountService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional
public class CashAccountServiceImpl implements CashAccountService {

    private final AccountRepository accountRepository;

    @Override
    public CashAccount createCashAccount(String accountNumber, String name, BigDecimal initialDeposit, String currency) {
        if (accountRepository.existsByAccountNumber(accountNumber)) {
            throw new IllegalArgumentException("Account number already exists: " + accountNumber);
        }

        CashAccount account = new CashAccount();
        account.setAccountNumber(accountNumber);
        account.setName(name);
        account.setCurrency(currency != null ? currency.toUpperCase() : "USD");
        account.setStatus(AccountStatus.ACTIVE);

        if (initialDeposit != null && initialDeposit.compareTo(BigDecimal.ZERO) > 0) {
            account.credit(initialDeposit);
        }

        return accountRepository.save(account);
    }
}