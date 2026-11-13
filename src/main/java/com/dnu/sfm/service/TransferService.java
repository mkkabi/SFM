package com.dnu.sfm.service;

import com.dnu.sfm.dto.TransferHistoryEntryDto;
import com.dnu.sfm.dto.TransferResultDto;

import java.math.BigDecimal;
import java.util.List;

public interface TransferService {

    /**
     * Moves the given amount from one account to another in a single atomic operation.
     * <p>
     * Allowed directions: cash account to cash account and cash account to
     * deposit account. Both accounts must be ACTIVE, must have the same
     * currency and the source must hold sufficient funds.
     * <p>
     * The transfer registers a transaction in <em>both</em> accounts:
     * a {@link com.dnu.sfm.model.TransactionType#DEBIT} on the source and a
     * {@link com.dnu.sfm.model.TransactionType#CREDIT} on the target.
     *
     * @param fromAccountId id of the source account
     * @param toAccountId   id of the target account
     * @param amount        amount to move, always positive
     * @return details of the completed transfer for user feedback
     * @throws com.dnu.sfm.exception.AccountNotFoundException  if either account does not exist
     * @throws com.dnu.sfm.exception.InactiveAccountException  if either account is not ACTIVE
     * @throws com.dnu.sfm.exception.CurrencyMismatchException if the accounts use different currencies
     * @throws IllegalArgumentException                        if the amount is null/not positive,
     *                                                         the source is not a cash account,
     *                                                         both ids are equal or funds are insufficient
     */
    TransferResultDto transfer(Long fromAccountId, Long toAccountId, BigDecimal amount);

    /**
     * Full history of completed cross-account movements, newest first.
     * <p>
     * Every transfer and every deposit-close payout records a DEBIT row on
     * the source and a CREDIT row on the target; both rows share a transfer
     * reference. This method pairs the rows back together and returns one
     * entry per movement.
     *
     * @return one entry per completed movement, possibly empty
     */
    List<TransferHistoryEntryDto> getTransferHistory();

}
