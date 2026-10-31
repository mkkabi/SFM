package com.dnu.sfm.service.impl;

import com.dnu.sfm.dto.TransactionDto;
import com.dnu.sfm.model.Account;
import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.Transaction;
import com.dnu.sfm.model.TransactionType;
import com.dnu.sfm.repository.TransactionRepository;
import com.dnu.sfm.service.ExchangeRateProviderService;
import com.dnu.sfm.service.TransactionService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TransactionServiceImp implements TransactionService {

    private static final MathContext PRECISION_CONTEXT = MathContext.DECIMAL64;
    private static final int DISPLAY_SCALE = 2;

    private final TransactionRepository transactionRepository;
    private final ExchangeRateProviderService exchangeRateProviderService;

    @Override
    public void recordTransaction(Account account, TransactionType type, BigDecimal amount) {
        Objects.requireNonNull(account, "Account must not be null");
        Objects.requireNonNull(type, "Transaction type must not be null");
        Objects.requireNonNull(amount, "Amount must not be null");

        LocalDate today = LocalDate.now();
        Map<String, BigDecimal> rates = exchangeRateProviderService.getEffectiveRatesForDate(today);

        BigDecimal rateUsd = rates.get("USD");
        BigDecimal rateEur = rates.get("EUR");

        // 1. Step: Convert account transaction amount into base currency (UAH)
        BigDecimal amountInUah = switch (account.getCurrency()) {
            case UAH -> amount;
            case USD -> amount.multiply(rateUsd, PRECISION_CONTEXT);
            case EUR -> amount.multiply(rateEur, PRECISION_CONTEXT);
        };

        // 2. Step: Convert UAH base amount to target foreign currencies
        BigDecimal amountInUsd = switch (account.getCurrency()) {
            case USD -> amount;
            case UAH, EUR -> amountInUah.divide(rateUsd, PRECISION_CONTEXT);
        };

        BigDecimal amountInEur = switch (account.getCurrency()) {
            case EUR -> amount;
            case UAH, USD -> amountInUah.divide(rateEur, PRECISION_CONTEXT);
        };

        Transaction transaction = new Transaction(
                account,
                type,
                amount,
                account.getCurrency(),
                account.getBalance(),
                amountInUah.setScale(DISPLAY_SCALE, RoundingMode.HALF_EVEN),
                amountInUsd.setScale(DISPLAY_SCALE, RoundingMode.HALF_EVEN),
                amountInEur.setScale(DISPLAY_SCALE, RoundingMode.HALF_EVEN),
                rateUsd.setScale(4, RoundingMode.HALF_EVEN),
                rateEur.setScale(4, RoundingMode.HALF_EVEN),
                LocalDateTime.now()
        );

        transactionRepository.save(transaction);

        log.info("Recorded multi-currency tx: accountId={}, type={}, native={} {}, UAH={}, USD={}, EUR={}",
                account.getId(), type, amount, account.getCurrency(),
                transaction.getAmountUah(), transaction.getAmountUsd(), transaction.getAmountEur());
    }

    @Override
    public List<TransactionDto> getTransactionsByAccount(Long accountId) {
        Objects.requireNonNull(accountId, "Account id must not be null");

        return transactionRepository.findByAccountIdOrderByTimestampDescIdDesc(accountId)
                .stream()
                .map(TransactionDto::from)
                .toList();
    }
}