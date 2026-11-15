package com.dnu.sfm.service.impl;

import com.dnu.sfm.dto.AccountCurrencyComparisonDto;
import com.dnu.sfm.dto.CurrencyOutcomeDto;
import com.dnu.sfm.model.Account;
import com.dnu.sfm.model.AccountStatus;
import com.dnu.sfm.model.DepositAccount;
import com.dnu.sfm.model.Transaction;
import com.dnu.sfm.model.TransactionType;
import com.dnu.sfm.repository.AccountRepository;
import com.dnu.sfm.repository.TransactionRepository;
import com.dnu.sfm.service.CurrencyAnalyticsService;
import com.dnu.sfm.service.ExchangeRateProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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

        boolean closedDeposit = account.getStatus() == AccountStatus.CLOSED
                && account instanceof DepositAccount;

        // 1. Replay ledger to obtain pure principal in each currency jar
        BigDecimal counterfactualUsd = BigDecimal.ZERO;
        BigDecimal counterfactualEur = BigDecimal.ZERO;
        BigDecimal counterfactualUah = BigDecimal.ZERO;

        for (Transaction tx : transactions) {
            // Exclude the close-deposit payout transfer leg so it is not treated as a user withdrawal.
            if (closedDeposit && tx.getType() == TransactionType.DEBIT && tx.getTransferRef() != null) {
                continue;
            }

            // Inflows: initial balance/deposits and incoming transfers.
            // Outflows: withdrawals and outgoing transfers.
            BigDecimal sign = (tx.getType() == TransactionType.DEPOSIT || tx.getType() == TransactionType.CREDIT)
                    ? BigDecimal.ONE
                    : BigDecimal.valueOf(-1);

            counterfactualUsd = counterfactualUsd.add(sign.multiply(tx.getAmountUsd()));
            counterfactualEur = counterfactualEur.add(sign.multiply(tx.getAmountEur()));
            counterfactualUah = counterfactualUah.add(sign.multiply(tx.getAmountUah()));
        }

        // 2. Determine actual principal (excluding interest)
        // For active accounts, account.getBalance() contains principal only.
        // For closed deposit accounts, balance was zeroed, so the replayed jar
        // for the account's own currency represents the true net principal invested.
        BigDecimal actualPrincipal = closedDeposit
                ? switch (account.getCurrency()) {
            case USD -> counterfactualUsd;
            case EUR -> counterfactualEur;
            case UAH -> counterfactualUah;
        }
                : account.getBalance();

        // 3. Fetch current exchange rates for valuation today
        Map<String, BigDecimal> todayRates = rateProviderService.getEffectiveRatesForDate(LocalDate.now());
        BigDecimal todayRateUsd = todayRates.get("USD");
        BigDecimal todayRateEur = todayRates.get("EUR");

        // Value of each pure currency position today in UAH
        BigDecimal valUsdInUah = counterfactualUsd.multiply(todayRateUsd).setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal valEurInUah = counterfactualEur.multiply(todayRateEur).setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal valUahInUah = counterfactualUah.setScale(2, RoundingMode.HALF_EVEN);

        // Baseline actual principal valued today in UAH
        BigDecimal actualValInUah = switch (account.getCurrency()) {
            case USD -> actualPrincipal.multiply(todayRateUsd).setScale(2, RoundingMode.HALF_EVEN);
            case EUR -> actualPrincipal.multiply(todayRateEur).setScale(2, RoundingMode.HALF_EVEN);
            case UAH -> actualPrincipal.setScale(2, RoundingMode.HALF_EVEN);
        };

        // 4. Build pure FX comparison list (no pinning needed)
        List<CurrencyOutcomeDto> alternatives = new ArrayList<>();
        alternatives.add(createOutcome("USD", counterfactualUsd, valUsdInUah, actualValInUah));
        alternatives.add(createOutcome("EUR", counterfactualEur, valEurInUah, actualValInUah));
        alternatives.add(createOutcome("UAH", counterfactualUah, valUahInUah, actualValInUah));

        // 5. Generate verdict based strictly on foreign exchange appreciation
        String verdict = buildVerdict(account.getCurrency().name(), alternatives);

        return new AccountCurrencyComparisonDto(
                account.getId(),
                account.getName(),
                account.getCurrency().name(),
                closedDeposit,
                actualPrincipal.setScale(2, RoundingMode.HALF_EVEN),
                actualValInUah,
                alternatives,
                verdict
        );
    }

    private CurrencyOutcomeDto createOutcome(String currency, BigDecimal nominal, BigDecimal valueInUahToday, BigDecimal actualUah) {
        BigDecimal diffUah = valueInUahToday.subtract(actualUah);
        BigDecimal pctDiff = BigDecimal.ZERO;
        if (actualUah.signum() > 0) {
            pctDiff = diffUah.divide(actualUah, 4, RoundingMode.HALF_EVEN)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_EVEN);
        }
        return new CurrencyOutcomeDto(
                currency,
                nominal.setScale(2, RoundingMode.HALF_EVEN),
                valueInUahToday,
                diffUah,
                pctDiff
        );
    }

    private String buildVerdict(String currentCurrency, List<CurrencyOutcomeDto> outcomes) {
        CurrencyOutcomeDto best = outcomes.stream()
                .max(Comparator.comparing(CurrencyOutcomeDto::valueInBaseCurrencyToday))
                .orElse(null);

        if (best != null && best.currencyCode().equalsIgnoreCase(currentCurrency)) {
            return "Holding " + currentCurrency + " was the optimal strategy over this timeframe.";
        } else if (best != null) {
            return "Holding " + best.currencyCode() + " would have yielded "
                    + best.differenceVersusActualUah().abs() + " UAH more ("
                    + best.percentageDifferenceVersusActual().abs() + "%) than holding "
                    + currentCurrency + " due to exchange rate changes.";
        }
        return "Not enough transaction data to determine an outcome.";
    }
}