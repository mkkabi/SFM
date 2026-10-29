package com.dnu.sfm.service;

import com.dnu.sfm.model.Account;
import com.dnu.sfm.model.AccountStatus;

import java.math.BigDecimal;
import java.util.List;

public interface AccountService {

    Account getAccountByNumber(String accountNumber);

    Account findById(Long id);

    List<Account> getAllAccounts();

    void deposit(String accountNumber, BigDecimal amount);

    void withdraw(String accountNumber, BigDecimal amount);

    void transfer(String fromAccountNumber, String toAccountNumber, BigDecimal amount);

    void updateAccountStatus(String accountNumber, AccountStatus newStatus);

    void closeAccount(String accountNumber);
}