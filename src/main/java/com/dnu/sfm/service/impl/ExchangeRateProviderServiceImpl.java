package com.dnu.sfm.service.impl;

import com.dnu.sfm.client.NbuExchangeRateClient;
import com.dnu.sfm.model.ExchangeRate;
import com.dnu.sfm.service.ExchangeRateProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateProviderServiceImpl implements ExchangeRateProviderService {

    private final NbuExchangeRateClient nbuClient;
    private final Map<LocalDate, Map<String, BigDecimal>> rateCache = new ConcurrentHashMap<>();

    @Override
    public Map<String, BigDecimal> getEffectiveRatesForDate(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }

        if (rateCache.containsKey(date)) {
            return rateCache.get(date);
        }

        LocalDate lookupDate = date;
        Map<String, BigDecimal> rates = Collections.emptyMap();
        int attempts = 0;

        // Fall back up to 5 days in case of weekends or official holidays
        while (rates.isEmpty() && attempts < 5) {
            try {
                List<ExchangeRate> nbuRates = nbuClient.getRatesForDate(lookupDate);
                if (nbuRates != null && !nbuRates.isEmpty()) {
                    rates = extractUsdAndEur(nbuRates);
                }
            } catch (Exception ex) {
                log.warn("Could not fetch NBU rates for {}: {}", lookupDate, ex.getMessage());
            }

            if (rates.isEmpty()) {
                lookupDate = lookupDate.minusDays(1);
                attempts++;
            }
        }

        if (rates.isEmpty()) {
            throw new IllegalStateException("Unable to retrieve valid exchange rates from NBU for date: " + date);
        }

        rateCache.put(date, rates);
        return rates;
    }

    private Map<String, BigDecimal> extractUsdAndEur(List<ExchangeRate> rates) {
        Map<String, BigDecimal> extracted = new HashMap<>();
        for (ExchangeRate rate : rates) {
            if ("USD".equalsIgnoreCase(rate.getCurrencyCode())) {
                extracted.put("USD", rate.getRate());
            } else if ("EUR".equalsIgnoreCase(rate.getCurrencyCode())) {
                extracted.put("EUR", rate.getRate());
            }
        }
        return (extracted.containsKey("USD") && extracted.containsKey("EUR")) ? extracted : Collections.emptyMap();
    }
}