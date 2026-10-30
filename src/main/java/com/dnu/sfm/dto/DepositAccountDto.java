package com.dnu.sfm.dto;

import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.DepositAccount;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Data Transfer Object representing a Deposit Account.
 * Implemented as an immutable Java record.
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

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "Maturity date is required")
        LocalDate maturityDate,

        BigDecimal accruedInterest
) {

    /**
     * Compact Constructor.
     * In a record, this constructor runs automatically during instantiation.
     * It allows validating invariants without having to re-declare the parameter list.
     */
    public DepositAccountDto {
        if (startDate != null && maturityDate != null && !maturityDate.isAfter(startDate)) {
            throw new IllegalArgumentException("Maturity date must be strictly after the start date.");
        }
    }

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
                LocalDate.now(),
                LocalDate.now().plusYears(1),
                BigDecimal.ZERO
        );
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
                account.getStartDate(),
                account.getMaturityDate(),
                account.getAccruedInterest()
        );
    }

    /**
     * Converts this DTO back to a JPA DepositAccount entity.
     */
    public DepositAccount toEntity() {
        DepositAccount account = new DepositAccount(
                this.name(),
                this.balance(),
                this.currency(),
                this.interestRate(),
                this.startDate(),
                this.maturityDate()
        );
        if (this.accruedInterest() != null) {
            account.setAccruedInterest(this.accruedInterest());
        }
        return account;
    }
}