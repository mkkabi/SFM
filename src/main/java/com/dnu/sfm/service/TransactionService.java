package com.dnu.sfm.service;

import com.dnu.sfm.dto.TransactionDto;
import com.dnu.sfm.model.Account;
import com.dnu.sfm.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionService {

    /**
     * Persists a record of a completed money movement (deposit or withdrawal)
     * made with the given account. The balance snapshot is taken from the
     * account's current (post-operation) balance.
     *
     * @param account the account the transaction was made with
     * @param type    direction of the movement (DEPOSIT or WITHDRAWAL)
     * @param amount  amount of money moved, always positive
     */
    void recordTransaction(Account account, TransactionType type, BigDecimal amount);

    /**
     * Records a transaction with a specific historical or custom timestamp.
     */
    void recordTransaction(Account account, TransactionType type, BigDecimal amount, LocalDateTime timestamp);

    /**
     * Full transaction history of one account, newest first.
     *
     * @param accountId id of the account
     * @return list of transactions, possibly empty if none were made yet
     */
    List<TransactionDto> getTransactionsByAccount(Long accountId);

}
