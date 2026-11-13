package com.dnu.sfm.dto;

import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.DepositAccount;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Data Transfer Object representing a Deposit Account.
 * Implemented as an immutable Java record.
 * <p>
 * The user chooses a start date and a term in months; the maturity date is
 * derived from them and never entered by hand.
 */
public record DepositAccountDto(
        Long id,

        @NotBlank(message = "Account name is mandatory")
        @Size(min = 2, max = 100, message = "Account name must be between 2 and 100 characters")
        String name,

        @NotNull(message = "Balance / initial deposit is required")
        @DecimalMin(value = "0.00", message = "Balance cannot be negative")
        @Digits(integer = 15, fraction = 2, message = "Invalid monetary amount precision (max 2 decimal places)")
        BigDecimal balance,

        @NotNull(message = "Currency is required")
        Currency currency,

        @NotNull(message = "Interest rate is required")
        @DecimalMin(value = "0.0000", message = "Interest rate cannot be negative")
        @DecimalMax(value = "100.0000", message = "Interest rate cannot exceed 100%")
        @Digits(integer = 3, fraction = 4, message = "Interest rate format invalid (max 3 integer digits, 4 decimals)")
        BigDecimal interestRate,

        @NotNull(message = "Tax rate is required")
        @DecimalMin(value = "0.0000", message = "Tax rate cannot be negative")
        @DecimalMax(value = "100.0000", message = "Tax rate cannot exceed 100%")
        @Digits(integer = 3, fraction = 4, message = "Tax rate format invalid (max 3 integer digits, 4 decimals)")
        BigDecimal taxRate,

        @NotNull(message = "Start date is required")
        @PastOrPresent(message = "Start date cannot be in the future")
        LocalDate startDate,

        /**
         * Deposit term in months chosen by the user; the maturity date is
         * calculated as {@code startDate + termMonths} months.
         */
        @NotNull(message = "Term in months is required")
        @Min(value = 1, message = "Term must be at least 1 month")
        @Max(value = 600, message = "Term cannot exceed 600 months (50 years)")
        Integer termMonths,

        BigDecimal accruedInterest
) {

    /**
     * No-args overloaded constructor.
     * Enables initializing an empty instance (e.g. for Spring/Thymeleaf form binding: new DepositAccountDto()).
     */
    public DepositAccountDto() {
        this(
                null,
                "",
                BigDecimal.ZERO,
                null,
                BigDecimal.ZERO,
                new BigDecimal("5.0000"), // Default TAX to 5.00%
                LocalDate.now(),
                12, // Default term: 12 months
                BigDecimal.ZERO
        );
    }

    /**
     * Maturity date derived from the start date and the selected term in months.
     * {@link LocalDate#plusMonths} clamps the day-of-month for shorter months
     * (e.g. Jan 31 + 1 month = Feb 28/29).
     */
    public LocalDate computedMaturityDate() {
        return (startDate == null || termMonths == null)
                ? null
                : startDate.plusMonths(termMonths);
    }

    /**
     * Cross-field validation: the derived maturity date must fall strictly after the start date,
     * i.e. the term must be at least one full month.
     * Declared as a Bean Validation constraint (instead of throwing from the compact
     * constructor) so that Spring MVC can report it as a form error and re-render
     * the form with the user's input preserved.
     */
    @AssertTrue(message = "The term must be at least 1 month so that the maturity date falls after the start date.")
    public boolean isDateRangeValid() {
        LocalDate maturity = computedMaturityDate();
        return startDate == null || termMonths == null || maturity.isAfter(startDate);
    }

    /**
     * Converts a JPA entity to this DTO.
     */
    public static DepositAccountDto fromEntity(DepositAccount account) {
        if (account == null) {
            return null;
        }
        return new DepositAccountDto(
                account.getId(),
                account.getName(),
                account.getBalance(),
                account.getCurrency(),
                account.getInterestRate(),
                account.getTaxRate(),
                account.getStartDate(),
                (int) ChronoUnit.MONTHS.between(account.getStartDate(), account.getMaturityDate()),
                account.getAccruedInterest()
        );
    }

    public DepositAccount toEntity() {
        DepositAccount account = new DepositAccount(
                this.name(),
                this.balance(),
                this.currency(),
                this.interestRate(),
                this.taxRate(),
                this.startDate(),
                computedMaturityDate()
        );
        if (this.accruedInterest() != null) {
            account.setAccruedInterest(this.accruedInterest());
        }
        return account;
    }
}
