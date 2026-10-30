package com.dnu.sfm.service;

import com.dnu.sfm.dto.DepositAccountDto;
import com.dnu.sfm.model.DepositAccount;

public interface DepositAccountService {


    DepositAccount createDepositAccount(DepositAccountDto depositAccountDto);

}
