package com.dnu.sfm.dto;

import com.dnu.sfm.model.Currency;

import java.math.BigDecimal;

/**
 * Result of closing a deposit account at maturity: the payout
 * (principal + net interest) has been transferred to the selected
 * target account and the deposit has been closed.
 */
public record DepositCloseResultDto(
        Long depositAccountId,
        String depositAccountName,
        BigDecimal principal,
        BigDecimal netInterest,
        BigDecimal payout,
        Currency currency,
        Long targetAccountId,
        String targetAccountName
) {
}
