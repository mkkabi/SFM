package com.dnu.sfm.service.impl;

import com.dnu.sfm.dto.PortfolioSummaryDto;
import com.dnu.sfm.model.Account;
import com.dnu.sfm.model.Currency;
import com.dnu.sfm.service.AccountService;
import com.dnu.sfm.service.ExchangeRateProviderService;
import com.dnu.sfm.service.PortfolioValuationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation: aggregates account principal per currency and values
 * the portfolio in UAH at the current NBU exchange rate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortfolioValuationServiceImpl implements PortfolioValuationService {

    private static final int UAH_SCALE = 2;

    private final AccountService accountService;
    private final ExchangeRateProviderService exchangeRateProviderService;

    @Override
    public PortfolioSummaryDto getPortfolioSummary() {
        List<Account> accounts = accountService.getAllAccounts();

        // Principal per currency, initialized with every currency of the Currency
        // enum so the view can render a complete breakdown. Deposit interest is
        // deliberately excluded: it is stored in DepositAccount.accruedInterest
        // and never part of Account.balance.
        Map<Currency, BigDecimal> balancesByCurrency = new EnumMap<>(Currency.class);
        for (Currency currency : Currency.values()) {
            balancesByCurrency.put(currency, BigDecimal.ZERO);
        }
        for (Account account : accounts) {
            if (account.getCurrency() == null || account.getBalance() == null) {
                continue;
            }
            balancesByCurrency.merge(account.getCurrency(), account.getBalance(), BigDecimal::add);
        }

        Map<Currency, BigDecimal> ratesToUah = new EnumMap<>(Currency.class);
        ratesToUah.put(Currency.UAH, BigDecimal.ONE);
        BigDecimal totalInUah = null;

        try {
            LocalDate today = LocalDate.now();
            Map<String, BigDecimal> effectiveRates = exchangeRateProviderService.getEffectiveRatesForDate(today);

            // The provider guarantees a rate for every enum currency or throws,
            // so a missing entry only means the API contract changed - treat it
            // as "cannot value the portfolio" rather than skipping the currency.
            BigDecimal sum = BigDecimal.ZERO;
            boolean allCurrenciesValued = true;
            for (Currency currency : Currency.values()) {
                BigDecimal rate;
                if (currency == Currency.UAH) {
                    rate = BigDecimal.ONE;
                } else {
                    rate = effectiveRates.get(currency.name());
                }
                if (rate == null) {
                    allCurrenciesValued = false;
                    break;
                }
                ratesToUah.put(currency, rate);
                sum = sum.add(balancesByCurrency.get(currency).multiply(rate));
            }

            if (allCurrenciesValued) {
                totalInUah = sum.setScale(UAH_SCALE, RoundingMode.HALF_UP);
            } else {
                log.warn("NBU rates are missing for at least one portfolio currency - total in UAH not available");
            }
        } catch (Exception ex) {
            log.warn("Could not value portfolio at current NBU rates: {}", ex.getMessage());
        }

        return new PortfolioSummaryDto(
                balancesByCurrency,
                ratesToUah,
                totalInUah,
                LocalDate.now(),
                accounts.size()
        );
    }
}
