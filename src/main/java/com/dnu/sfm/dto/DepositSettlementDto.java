package com.dnu.sfm.dto;

import java.math.BigDecimal;
import java.util.List;

public record DepositSettlementDto(
        Long accountId,
        BigDecimal totalPrincipal,
        BigDecimal grossInterest,
        BigDecimal taxWithheld,
        BigDecimal netInterest,
        BigDecimal finalPayout,
        List<TrancheInterestBreakdown> tranches
) {
    public record TrancheInterestBreakdown(
            BigDecimal amount,
            long daysInvested,
            BigDecimal grossEarned
    ) {}
}