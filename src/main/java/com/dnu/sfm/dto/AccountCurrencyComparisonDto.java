package com.dnu.sfm.dto;

import java.math.BigDecimal;
import java.util.List;

public record AccountCurrencyComparisonDto(
        Long accountId,
        String accountName,
        String accountCurrency,
        BigDecimal actualBalance,
        BigDecimal actualValueInUahToday,
        List<CurrencyOutcomeDto> alternatives,
        String recommendationVerdict
) {}