package com.dnu.sfm.service.impl;

import com.dnu.sfm.dto.AccountCurrencyComparisonDto;
import com.dnu.sfm.dto.CurrencyOutcomeDto;
import com.dnu.sfm.model.Account;
import com.dnu.sfm.model.Transaction;
import com.dnu.sfm.model.TransactionType;
import com.dnu.sfm.repository.AccountRepository;
import com.dnu.sfm.repository.TransactionRepository;
import com.dnu.sfm.service.CurrencyAnalyticsService;
import com.dnu.sfm.service.ExchangeRateProviderService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CurrencyAnalyticsServiceImpl implements CurrencyAnalyticsService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final ExchangeRateProviderService rateProviderService;

    @Override
    public AccountCurrencyComparisonDto compareCurrencyOutcomes(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found with ID: " + accountId));

        List<Transaction> transactions = transactionRepository.findByAccountIdOrderByTimestampDescIdDesc(accountId);

        // 1. Replay ledger to obtain counterfactual final balances
        BigDecimal counterfactualUsd = BigDecimal.ZERO;
        BigDecimal counterfactualEur = BigDecimal.ZERO;
        BigDecimal counterfactualUah = BigDecimal.ZERO;

        for (Transaction tx : transactions) {
            BigDecimal sign = (tx.getType() == TransactionType.DEPOSIT) ? BigDecimal.ONE : BigDecimal.valueOf(-1);

            counterfactualUsd = counterfactualUsd.add(sign.multiply(tx.getAmountUsd()));
            counterfactualEur = counterfactualEur.add(sign.multiply(tx.getAmountEur()));
            counterfactualUah = counterfactualUah.add(sign.multiply(tx.getAmountUah()));
        }

        // 2. Fetch current rates for today's valuation in UAH
        Map<String, BigDecimal> todayRates = rateProviderService.getEffectiveRatesForDate(LocalDate.now());
        BigDecimal todayRateUsd = todayRates.get("USD");
        BigDecimal todayRateEur = todayRates.get("EUR");

        // Value of each currency position today in UAH
        BigDecimal valUsdInUah = counterfactualUsd.multiply(todayRateUsd).setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal valEurInUah = counterfactualEur.multiply(todayRateEur).setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal valUahInUah = counterfactualUah.setScale(2, RoundingMode.HALF_EVEN);

        // Actual account valuation today in UAH
        BigDecimal actualValInUah = switch (account.getCurrency()) {
            case USD -> account.getBalance().multiply(todayRateUsd).setScale(2, RoundingMode.HALF_EVEN);
            case EUR -> account.getBalance().multiply(todayRateEur).setScale(2, RoundingMode.HALF_EVEN);
            case UAH -> account.getBalance().setScale(2, RoundingMode.HALF_EVEN);
        };

        // 3. Build alternative comparison list
        List<CurrencyOutcomeDto> alternatives = new ArrayList<>();
        alternatives.add(createOutcome("USD", counterfactualUsd, valUsdInUah, actualValInUah));
        alternatives.add(createOutcome("EUR", counterfactualEur, valEurInUah, actualValInUah));
        alternatives.add(createOutcome("UAH", counterfactualUah, valUahInUah, actualValInUah));

        // 4. Generate user verdict
        String verdict = buildVerdict(account.getCurrency().name(), alternatives);

        return new AccountCurrencyComparisonDto(
                account.getId(),
                account.getName(),
                account.getCurrency().name(),
                account.getBalance(),
                actualValInUah,
                alternatives,
                verdict
        );
    }

    private CurrencyOutcomeDto createOutcome(String currency, BigDecimal nominal, BigDecimal valueInUahToday, BigDecimal actualUah) {
        BigDecimal diffUah = valueInUahToday.subtract(actualUah);
        BigDecimal pctDiff = BigDecimal.ZERO;
        if (actualUah.signum() > 0) {
            pctDiff = diffUah.divide(actualUah, 4, RoundingMode.HALF_EVEN).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_EVEN);
        }
        return new CurrencyOutcomeDto(currency, nominal.setScale(2, RoundingMode.HALF_EVEN), valueInUahToday, diffUah, pctDiff);
    }

    private String buildVerdict(String currentCurrency, List<CurrencyOutcomeDto> outcomes) {
        CurrencyOutcomeDto best = outcomes.stream()
                .max((a, b) -> a.valueInBaseCurrencyToday().compareTo(b.valueInBaseCurrencyToday()))
                .orElse(null);

        if (best != null && best.currencyCode().equalsIgnoreCase(currentCurrency)) {
            return "Holding " + currentCurrency + " was the optimal strategy over this timeframe.";
        } else if (best != null) {
            return "Holding " + best.currencyCode() + " would have yielded "
                    + best.differenceVersusActualUah().abs() + " UAH more ("
                    + best.percentageDifferenceVersusActual().abs() + "%) than your current " + currentCurrency + " strategy.";
        }
        return "Not enough transaction data to determine an outcome.";
    }
}