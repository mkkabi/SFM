package com.dnu.sfm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DepositSettlementDto(
        Long accountId,
        String accountName,
        String currency,
        BigDecimal interestRate,
        BigDecimal taxRate,
        LocalDate startDate,
        LocalDate maturityDate,
        long totalTermDays,
        BigDecimal totalPrincipal,
        BigDecimal grossInterest,
        BigDecimal taxWithheld,
        BigDecimal netInterest,
        BigDecimal finalPayout,
        List<TrancheBreakdownDto> tranches
) {
    public record TrancheBreakdownDto(
            LocalDate depositDate,
            boolean isInitial,
            BigDecimal amount,
            long daysInvested,
            BigDecimal grossEarned,
            BigDecimal netEarned
    ) {}
}