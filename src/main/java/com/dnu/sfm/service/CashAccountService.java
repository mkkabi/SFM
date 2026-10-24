package com.dnu.sfm.service;

import com.dnu.sfm.model.CashAccount;

import java.math.BigDecimal;

public interface CashAccountService {
    CashAccount createCashAccount(String accountNumber, String name, BigDecimal initialDeposit, String currency);
    // Future expansion: linkDebitCard(), setOverdraftLimit(), etc.
}