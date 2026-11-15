package com.dnu.sfm.service;

import com.dnu.sfm.dto.DepositAccountDto;
import com.dnu.sfm.dto.DepositCloseResultDto;
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
     * Closes a deposit account whose maturity date has been reached and pays
     * out the deposited amount (principal + net interest after tax) by
     * transferring it to the given target account.
     * <p>
     * The payout registers a transaction in both accounts: a
     * {@link com.dnu.sfm.model.TransactionType#DEBIT} on the deposit and a
     * {@link com.dnu.sfm.model.TransactionType#CREDIT} on the target. After a
     * successful close the deposit has a zero balance and the
     * {@link com.dnu.sfm.model.AccountStatus#CLOSED} status.
     *
     * @param id              id of the deposit account to close
     * @param targetAccountId id of the ACTIVE cash account receiving the payout
     * @return details of the payout for user feedback
     * @throws com.dnu.sfm.exception.MaturityNotReachedException if today is before the maturity date
     * @throws com.dnu.sfm.exception.InactiveAccountException    if the deposit or the target is not ACTIVE
     * @throws com.dnu.sfm.exception.AccountNotFoundException    if the deposit or the target does not exist
     * @throws com.dnu.sfm.exception.CurrencyMismatchException   if the target uses a different currency
     * @throws IllegalArgumentException                          if the target is not a cash account or
     *                                                           equals the deposit itself
     */
    DepositCloseResultDto closeDepositAccountAndTransfer(Long id, Long targetAccountId);

    /**
     * Prolongs the deposit term: the new maturity date is calculated as
     * {@code maturityDate + (maturityDate - startDate)}, i.e. the original term is doubled.
     * <p>
     * Not offered to users through the web UI - the extend option is hidden
     * from the user interface; the operation remains available programmatically.
     */
    DepositAccount extendMaturityDate(Long id);

}
