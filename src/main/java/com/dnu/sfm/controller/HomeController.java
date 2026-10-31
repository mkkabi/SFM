package com.dnu.sfm.controller;


import com.dnu.sfm.client.NbuExchangeRateClient;
import com.dnu.sfm.model.ExchangeRate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Slf4j
@Controller
@RequiredArgsConstructor
public class HomeController {

    private static final Set<String> FEATURED_CURRENCIES = Set.of("USD", "EUR", "PLN", "GBP");

    private final NbuExchangeRateClient nbuExchangeRateClient;

    @GetMapping({"/", "home"})
    public String home(Model model) {
        LocalDate today = LocalDate.now();
        List<ExchangeRate> todayRates = Collections.emptyList();

        try {
            List<ExchangeRate> allRates = nbuExchangeRateClient.getRatesForDate(today);
            if (allRates != null) {
                todayRates = allRates.stream()
                        .filter(rate -> FEATURED_CURRENCIES.contains(rate.getCurrencyCode()))
                        .toList();
            }
        } catch (Exception ex) {
            log.warn("Failed to fetch current NBU exchange rates: {}", ex.getMessage());
        }

        model.addAttribute("ratesDate", today);
        model.addAttribute("exchangeRates", todayRates);

        return "home";
    }


}
