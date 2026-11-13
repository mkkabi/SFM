package com.dnu.sfm.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Form-backing object for the "Transfer Funds" page.
 * <p>
 * A user can transfer funds between cash accounts and from a cash account
 * to a deposit account. Each transfer registers a transaction in both
 * accounts: a DEBIT on the source and a CREDIT on the target.
 */
public record TransferFormDto(
        @NotNull(message = "Source account is required")
        Long fromAccountId,

        @NotNull(message = "Target account is required")
        Long toAccountId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Transfer amount must be at least 0.01")
        @Digits(integer = 15, fraction = 2, message = "Invalid monetary amount precision (max 2 decimal places)")
        BigDecimal amount
) {

    /**
     * Empty constructor for form-backing initialization (e.g., new TransferFormDto()).
     */
    public TransferFormDto() {
        this(null, null, null);
    }

    /**
     * Cross-field validation: source and target must be different accounts.
     */
    @AssertTrue(message = "Source and target account must be different.")
    public boolean isAccountsValid() {
        return fromAccountId == null || toAccountId == null || !fromAccountId.equals(toAccountId);
    }
}
