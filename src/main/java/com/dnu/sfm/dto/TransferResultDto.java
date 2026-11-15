package com.dnu.sfm.dto;

import com.dnu.sfm.model.Currency;

import java.math.BigDecimal;

/**
 * Result of a completed transfer between two accounts.
 * <p>
 * A transfer always registers two transactions: a DEBIT on the source
 * account and a CREDIT on the target account.
 */
public record TransferResultDto(
        Long fromAccountId,
        String fromAccountName,
        Long toAccountId,
        String toAccountName,
        BigDecimal amount,
        Currency currency,
        BigDecimal fromBalanceAfter,
        BigDecimal toBalanceAfter,
        /** Reference shared by the DEBIT/CREDIT legs, visible on the transfer history page. */
        String transferRef
) {
}
