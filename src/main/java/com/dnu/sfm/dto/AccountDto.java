package com.dnu.sfm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record AccountDto(
        @NotBlank(message = "Account name is required")
        String name,

        String accountNumber,

        @NotBlank(message = "Account type is required")
        String accountType,

        @NotBlank(message = "Currency is required")
        String currency,

        @NotNull(message = "Initial balance is required")
        BigDecimal initialBalance,

        String institution,
        String description
) {}