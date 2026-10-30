package com.dnu.sfm.service;

import com.dnu.sfm.dto.DepositAccountDto;
import com.dnu.sfm.model.DepositAccount;

import java.math.BigDecimal;

public interface DepositAccountService {


    DepositAccount createDepositAccount(DepositAccountDto depositAccountDto);

    /**
     * Fetches a single deposit account by its identifier.
     *
     * @throws com.dnu.sfm.exception.AccountNotFoundException if no deposit account exists for the id
     */
    DepositAccount getDepositAccountById(Long id);

    /**
     * Adds the given amount to the deposit account balance.
     *
     * @throws com.dnu.sfm.exception.InactiveAccountException if the account is not ACTIVE
     * @throws IllegalArgumentException if the amount is null or not positive
     */
    DepositAccount depositToDepositAccount(Long id, BigDecimal amount);

    /**
     * Subtracts the given amount from the deposit account balance.
     * Allowed only on the maturity date of the deposit.
     *
     * @throws com.dnu.sfm.exception.MaturityNotReachedException if today is not the maturity date
     * @throws com.dnu.sfm.exception.InactiveAccountException if the account is not ACTIVE
     * @throws IllegalArgumentException if the amount is null, not positive or exceeds the balance
     */
    DepositAccount withdrawFromDepositAccount(Long id, BigDecimal amount);

    /**
     * Prolongs the deposit term: the new maturity date is calculated as
     * {@code maturityDate + (maturityDate - startDate)}, i.e. the original term is doubled.
     */
    DepositAccount extendMaturityDate(Long id);

}
