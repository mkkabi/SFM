package com.dnu.sfm.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of the counterfactual currency comparison for one account.
 * <p>
 * {@code accountClosed} marks a closed deposit account: the account balance
 * is zero after the close payout, so the "actual" side of the comparison
 * carries the payout the user received at close (principal + net interest)
 * instead of the emptied balance.
 */
public record AccountCurrencyComparisonDto(
        Long accountId,
        String accountName,
        String accountCurrency,
        boolean accountClosed,
        BigDecimal actualBalance,
        BigDecimal actualValueInUahToday,
        List<CurrencyOutcomeDto> alternatives,
        String recommendationVerdict
) {}
