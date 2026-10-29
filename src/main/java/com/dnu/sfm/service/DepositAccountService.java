package com.dnu.sfm.service;

import com.dnu.sfm.dto.AccountDto;
import com.dnu.sfm.dto.DepositAccountDto;
import com.dnu.sfm.model.DepositAccount;

public interface DepositAccountService {

    /**
     * Creates a deposit account from the simplified account form DTO
     * (the DTO bound by the "Add Account" web form).
     * <p>
     * Because the simplified form carries no deposit terms, the account is
     * opened as a non-interest-bearing deposit with a default 12-month term,
     * early withdrawal allowed and no penalty. A unique account number is
     * generated automatically when the caller leaves it blank.
     *
     * @param accountDto the simplified account data; must not be {@code null}
     * @return the persisted {@link DepositAccount}
     * @throws IllegalArgumentException if the DTO is invalid (blank name,
     *         malformed currency, negative balance, or duplicate account number)
     */
    DepositAccount createDepositAccount(AccountDto accountDto);

    /**
     * Creates a deposit account from the full deposit-specific DTO.
     * <p>
     * Mapping, duplicate-account-number checking and persistence are delegated
     * to {@code DepositAccountRepository#createAccount(DepositAccountDto)}.
     *
     * @param depositAccountDto the fully specified deposit account data; must not be {@code null}
     * @return the persisted {@link DepositAccount}
     * @throws IllegalArgumentException if the DTO is null or the account number already exists
     */
    DepositAccount createDepositAccount(DepositAccountDto depositAccountDto);

    void accrueDailyInterest(String accountNumber);
    void capitalizeInterest(String accountNumber);
}
