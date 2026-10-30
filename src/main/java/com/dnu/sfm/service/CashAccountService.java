package com.dnu.sfm.service;

import com.dnu.sfm.dto.AccountDto;
import com.dnu.sfm.model.Account;
import com.dnu.sfm.model.CashAccount;

import java.math.BigDecimal;

public interface CashAccountService {

    CashAccount createCashAccount(AccountDto accountDto);

}