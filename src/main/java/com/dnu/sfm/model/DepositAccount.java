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

    /**
     * Whether early liquidation/withdrawal is permitted prior to maturityDate.
     */
    @Column(nullable = false)
    private boolean earlyWithdrawalAllowed = false;

    /**
     * Penalty rate applied to early withdrawals (e.g., 1.50 for 1.50% fee on withdrawn sum).
     */
    @Column(precision = 7, scale = 4)
    private BigDecimal penaltyRate = BigDecimal.ZERO;

    public DepositAccount() {
        super();
    }

    public DepositAccount(
            String accountNumber,
            String name,
            BigDecimal initialDeposit,
            String currency,
            BigDecimal interestRate,
            LocalDate startDate,
            LocalDate maturityDate,
            boolean earlyWithdrawalAllowed,
            BigDecimal penaltyRate
    ) {
        super(accountNumber, name, initialDeposit, currency);

        if (maturityDate == null || startDate == null || maturityDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Maturity date must be after the start date.");
        }
        if (interestRate == null || interestRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Interest rate cannot be negative.");
        }

        this.interestRate = interestRate;
        this.startDate = startDate;
        this.maturityDate = maturityDate;
        this.earlyWithdrawalAllowed = earlyWithdrawalAllowed;
        this.penaltyRate = penaltyRate != null ? penaltyRate : BigDecimal.ZERO;
    }

    // --- Financial Invariants & Operations ---

    /**
     * Accrues simple daily interest based on Actual/365 convention:
     * Daily Interest = Balance * (Rate / 100) / 365
     */
    public void accrueDailyInterest() {
        if (LocalDate.now().isAfter(maturityDate)) {
            return; // Term has matured; accrual stops unless rolled over
        }

        BigDecimal rateFraction = this.interestRate
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_EVEN);

        BigDecimal dailyRate = rateFraction
                .divide(BigDecimal.valueOf(365), 8, RoundingMode.HALF_EVEN);

        BigDecimal dailyAccrual = getBalance()
                .multiply(dailyRate)
                .setScale(4, RoundingMode.HALF_EVEN);

        this.accruedInterest = this.accruedInterest.add(dailyAccrual);
    }

    /**
     * Capitalizes accumulated accrued interest into the active principal balance.
     */
    public void capitalizeInterest() {
        if (this.accruedInterest.compareTo(BigDecimal.ZERO) > 0) {
            credit(this.accruedInterest);
            this.accruedInterest = BigDecimal.ZERO;
        }
    }

    /**
     * Enforces term deposit withdrawal restrictions:
     * - Disallows debit before maturity if early withdrawal is disabled.
     * - Applies penalty deduction if liquidated prematurely.
     */
    @Override
    public void debit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive.");
        }

        boolean isPremature = LocalDate.now().isBefore(this.maturityDate);

        if (isPremature && !this.earlyWithdrawalAllowed) {
            throw new IllegalStateException(
                    "Funds are locked until maturity date: " + this.maturityDate
            );
        }

        BigDecimal totalDeduction = amount;

        // Apply premature liquidation fee if broken before maturity
        if (isPremature && this.penaltyRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal penaltyFraction = this.penaltyRate
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_EVEN);
            BigDecimal penaltyFee = amount.multiply(penaltyFraction).setScale(4, RoundingMode.HALF_EVEN);
            totalDeduction = totalDeduction.add(penaltyFee);
        }

        if (getBalance().compareTo(totalDeduction) < 0) {
            throw new IllegalArgumentException(
                    "Insufficient balance to cover withdrawal and penalty. Required: " + totalDeduction
            );
        }

        setBalance(getBalance().subtract(totalDeduction));
    }

    public boolean isMatured() {
        return !LocalDate.now().isBefore(this.maturityDate);
    }

    public long getRemainingDays() {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), this.maturityDate);
        return Math.max(0, days);
    }


}