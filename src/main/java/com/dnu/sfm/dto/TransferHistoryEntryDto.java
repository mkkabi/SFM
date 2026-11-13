package com.dnu.sfm.dto;

import com.dnu.sfm.model.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * One row of the cross-account transfer history: a completed movement of
 * money between two accounts, rebuilt from the paired DEBIT/CREDIT
 * transactions that share the same {@code transferRef}.
 *
 * @param transferRef     reference shared by the two legs of the movement
 * @param kind            what kind of movement it was (plain transfer or
 *                        deposit close payout)
 * @param timestamp       moment the movement was executed
 * @param fromAccountId   id of the account the money left
 * @param fromAccountName name of the account the money left
 * @param fromAccountType "Cash" or "Deposit"
 * @param toAccountId     id of the account that received the money
 * @param toAccountName   name of the account that received the money
 * @param toAccountType   "Cash" or "Deposit"
 * @param amount          moved amount, always positive
 * @param currency        currency of both accounts (transfers are same-currency)
 */
public record TransferHistoryEntryDto(
        String transferRef,
        TransferKind kind,
        LocalDateTime timestamp,
        Long fromAccountId,
        String fromAccountName,
        String fromAccountType,
        Long toAccountId,
        String toAccountName,
        String toAccountType,
        BigDecimal amount,
        Currency currency) {

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** What the movement was: a user-initiated transfer or an automatic deposit payout. */
    public enum TransferKind {
        /** Transfer made from the "Transfer Funds" page (cash to cash or cash to deposit). */
        ACCOUNT_TRANSFER,
        /** Payout executed automatically when a matured deposit was closed. */
        DEPOSIT_CLOSE;

        /** Label shown in the history table. */
        public String getLabel() {
            return switch (this) {
                case ACCOUNT_TRANSFER -> "Transfer";
                case DEPOSIT_CLOSE -> "Deposit close";
            };
        }
    }

    public String formattedTimestamp() {
        return timestamp != null ? timestamp.format(DISPLAY_FORMAT) : "";
    }

    /** Short reference for display, e.g. {@code TRF-3F9A2C1B8D4E}. */
    public String reference() {
        return transferRef;
    }

    /** Amount with currency, always shown with two decimals, e.g. {@code 250.00 USD}. */
    public String amountLabel() {
        return amount != null
                ? amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString() + " " + currency
                : "";
    }
}
