package com.dnu.sfm.service.impl;

import com.dnu.sfm.dto.AccountDto;
import com.dnu.sfm.model.AccountStatus;
import com.dnu.sfm.model.CashAccount;
import com.dnu.sfm.model.Currency;
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
    public CashAccount createCashAccount(AccountDto accountDto) {
        return null;
    }
}