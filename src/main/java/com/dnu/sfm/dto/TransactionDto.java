package com.dnu.sfm.dto;

import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.Transaction;
import com.dnu.sfm.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Read-only view of a single transaction, used to render the
 * transaction history page of an account.
 */

public record TransactionDto(
        Long id,
        Long accountId,
        String accountName,
        TransactionType type,
        BigDecimal amount,
        Currency currency,
        BigDecimal balanceAfter,
        BigDecimal amountUah,
        BigDecimal amountUsd,
        BigDecimal amountEur,
        BigDecimal rateUsdToUah,
        BigDecimal rateEurToUah,
        LocalDateTime timestamp) {

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static TransactionDto from(Transaction transaction) {
        return new TransactionDto(
                transaction.getId(),
                transaction.getAccount().getId(),
                transaction.getAccount().getName(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getBalanceAfter(),
                transaction.getAmountUah(),
                transaction.getAmountUsd(),
                transaction.getAmountEur(),
                transaction.getRateUsdToUah(),
                transaction.getRateEurToUah(),
                transaction.getTimestamp()
        );
    }

    public String formattedTimestamp() {
        return timestamp != null ? timestamp.format(DISPLAY_FORMAT) : "";
    }

    public String signedAmount() {
        return (type == TransactionType.DEPOSIT ? "+" : "-")
                + amount.toPlainString() + " " + currency;
    }
}