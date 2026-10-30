package com.dnu.sfm.service;

import com.dnu.sfm.dto.AccountDto;
import com.dnu.sfm.model.CashAccount;

public interface CashAccountService {

    CashAccount createCashAccount(AccountDto accountDto);

}