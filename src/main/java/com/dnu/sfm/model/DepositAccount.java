package com.dnu.sfm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Getter
@Setter
@Entity
@Table(name = "deposit_accounts")
public class DepositAccount extends Account {

    /**
     * Annual nominal interest rate as a percentage (e.g., 5.50 for 5.50% p.a.).
     */
    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal interestRate;

    /**
     * Opening date of the deposit term.
     */
    @Column(nullable = false)
    private LocalDate startDate;

    /**
     * Maturity date when the principal and interest become unlocked.
     */
    @Column(nullable = false)
    private LocalDate maturityDate;

    /**
     * Total interest accrued and credited or waiting to be capitalized.
     */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal accruedInterest = BigDecimal.ZERO;


    public DepositAccount() {
        super();
    }

    public DepositAccount(
            String name,
            BigDecimal initialDeposit,
            Currency currency,
            BigDecimal interestRate,
            LocalDate startDate,
            LocalDate maturityDate
    ) {
        super(name, initialDeposit, currency);

        if (maturityDate == null || startDate == null || maturityDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Maturity date must be after the start date.");
        }
        if (interestRate == null || interestRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Interest rate cannot be negative.");
        }

        this.interestRate = interestRate;
        this.startDate = startDate;
        this.maturityDate = maturityDate;

    }

}