package com.dnu.sfm.service;

import com.dnu.sfm.model.Account;

import java.util.List;

public interface AccountService {

    List<Account> getAllAccounts();

    /**
     * Fetches a single account by its identifier.
     *
     * @throws com.dnu.sfm.exception.AccountNotFoundException if no account exists for the id
     */
    Account getAccountById(Long id);

}
