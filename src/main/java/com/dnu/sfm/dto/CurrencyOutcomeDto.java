package com.dnu.sfm.dto;

import java.math.BigDecimal;

public record CurrencyOutcomeDto(
        String currencyCode,
        BigDecimal nominalBalance,
        BigDecimal valueInBaseCurrencyToday,
        BigDecimal differenceVersusActualUah,
        BigDecimal percentageDifferenceVersusActual
) {}