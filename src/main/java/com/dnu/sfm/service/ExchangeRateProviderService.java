package com.dnu.sfm.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public interface ExchangeRateProviderService {

    /**
     * Returns a map containing the rates for USD and EUR quoted against UAH.
     * e.g., "USD" -> 41.5000, "EUR" -> 44.8000
     */
    Map<String, BigDecimal> getEffectiveRatesForDate(LocalDate date);
}