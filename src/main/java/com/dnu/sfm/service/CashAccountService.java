package com.dnu.sfm.service;

import com.dnu.sfm.dto.AccountDto;
import com.dnu.sfm.model.AccountStatus;
import com.dnu.sfm.model.CashAccount;

import java.math.BigDecimal;

public interface CashAccountService {

    CashAccount createCashAccount(AccountDto accountDto);

    /**
     * Fetches a single cash account by its identifier.
     *
     * @throws com.dnu.sfm.exception.AccountNotFoundException if no cash account exists for the id
     */
    CashAccount getCashAccountById(Long id);

    /**
     * Adds the given amount to the cash account balance.
     *
     * @throws com.dnu.sfm.exception.InactiveAccountException if the account is not ACTIVE
     * @throws IllegalArgumentException if the amount is null or not positive
     */
    CashAccount depositToCashAccount(Long id, BigDecimal amount);

    /**
     * Subtracts the given amount from the cash account balance.
     *
     * @throws com.dnu.sfm.exception.InactiveAccountException if the account is not ACTIVE
     * @throws IllegalArgumentException if the amount is null, not positive or exceeds the balance
     */
    CashAccount withdrawFromCashAccount(Long id, BigDecimal amount);

    /**
     * Changes the status of the cash account (e.g. ACTIVE, FROZEN, CLOSED).
     */
    CashAccount changeAccountStatus(Long id, AccountStatus newStatus);

}