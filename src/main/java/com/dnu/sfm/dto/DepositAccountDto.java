package com.dnu.sfm.dto;



import com.dnu.sfm.model.AccountStatus;
import com.dnu.sfm.model.CompoundingFrequency;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepositAccountDto {

    private UUID id;

    @NotNull(message = "Customer ID is required")
    private UUID customerId;

    @NotBlank(message = "Account number is required")
    private String accountNumber;

    @NotBlank(message = "Account name is required")
    private String accountName;

    @NotNull(message = "Currency code is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must adhere to ISO 4217 standard")
    private String currency;

    @NotNull(message = "Principal amount is required")
    @DecimalMin(value = "0.00", inclusive = false)
    @Digits(integer = 15, fraction = 2)
    private BigDecimal principalAmount;

    @NotNull(message = "Current balance is required")
    @DecimalMin(value = "0.00")
    @Digits(integer = 15, fraction = 2)
    private BigDecimal currentBalance;

    @NotNull(message = "Interest rate is required")
    @Digits(integer = 3, fraction = 4)
    private BigDecimal annualInterestRate;

    @NotNull(message = "Compounding frequency is required")
    private CompoundingFrequency compoundingFrequency;

    @NotNull(message = "Term duration is required")
    @Positive
    private Integer termMonths;

    @NotNull(message = "Start date is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @NotNull(message = "Maturity date is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate maturityDate;

    @NotNull(message = "Account status is required")
    private AccountStatus status;

    @Digits(integer = 15, fraction = 2)
    private BigDecimal accruedInterest;

    @Digits(integer = 3, fraction = 4)
    private BigDecimal earlyWithdrawalPenaltyRate;

    private boolean autoRenewal;
}