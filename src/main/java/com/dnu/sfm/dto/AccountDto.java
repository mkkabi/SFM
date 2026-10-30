package com.dnu.sfm.dto;

import com.dnu.sfm.model.Account;
import com.dnu.sfm.model.AccountStatus;
import com.dnu.sfm.model.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Base Data Transfer Object for account operations.
 * Implemented as an immutable record with compact validation
 * and an explicit no-args constructor for Spring/Thymeleaf form binding.
 */
public record AccountDto(
        Long id,

        @NotBlank(message = "Account name is required")
        @Size(min = 2, max = 100, message = "Account name must be between 2 and 100 characters")
        String name,

        @NotNull(message = "Balance is required")
        @DecimalMin(value = "0.00", message = "Balance cannot be negative")
        @Digits(integer = 15, fraction = 2, message = "Invalid monetary amount precision (max 2 decimal places)")
        BigDecimal balance,

        @NotNull(message = "Currency is required")
        Currency currency,

        @NotNull(message = "Account status is required")
        AccountStatus status,

        @NotNull
        LocalDate startDate

) {

        /**
         * Compact constructor: normalizes null defaults and enforces domain invariants.
         */
        public AccountDto {
                balance = balance != null ? balance : BigDecimal.ZERO;
                currency = currency != null ? currency : Currency.USD;
                status = status != null ? status : AccountStatus.ACTIVE;
                startDate = startDate != null ? startDate : LocalDate.now();
        }

        /**
         * Empty constructor for form-backing initialization (e.g., new AccountDto()).
         */
        public AccountDto() {
                this(null, "", BigDecimal.ZERO, Currency.USD, AccountStatus.ACTIVE, LocalDate.now());
        }

        /**
         * Maps an Account entity to an AccountDto instance.
         */
        public static AccountDto fromEntity(Account account) {
                if (account == null) {
                        return null;
                }
                return new AccountDto(
                        account.getId(),
                        account.getName(),
                        account.getBalance(),
                        account.getCurrency(),
                        account.getStatus(),
                        account.getStartDate()
                );
        }
}