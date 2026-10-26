package com.dnu.sfm.dto;

import com.dnu.sfm.model.CompoundingFrequency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CreateDepositAccountRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Currency code is required")
    @Size(min = 3, max = 3, message = "Currency must follow ISO 4217 (e.g., USD, EUR)")
    private String currency;

    @NotNull(message = "Initial deposit amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 15, fraction = 2, message = "Format: up to 15 digits and 2 decimal places")
    private BigDecimal initialDepositAmount;

    @NotNull(message = "Annual interest rate is required")
    @Digits(integer = 1, fraction = 4, message = "Interest rate scale cannot exceed 4 decimal places")
    private BigDecimal annualInterestRate; // e.g., 0.0450 for 4.5%

    @NotNull(message = "Term in months is required")
    private Integer termInMonths;

    private CompoundingFrequency compoundingFrequency;

    private Boolean autoRenewal;
}