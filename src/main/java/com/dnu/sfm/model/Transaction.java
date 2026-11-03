package com.dnu.sfm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single money movement (deposit or withdrawal) made with an account.
 * <p>
 * Every successful deposit/withdrawal applied by
 * {@code CashAccountService} or {@code DepositAccountService} is recorded
 * here, so the app can show the full history of any account.
 */

@Setter
@Getter
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    /** Native account amount moved */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    /** Multi-currency equivalent amounts calculated at transaction time */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amountUah;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amountUsd;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amountEur;

    /** NBU official exchange rates applied at transaction time */
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal rateUsdToUah;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal rateEurToUah;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    public Transaction() {
    }

    public Transaction(Account account,
                       TransactionType type,
                       BigDecimal amount,
                       Currency currency,
                       BigDecimal balanceAfter,
                       BigDecimal amountUah,
                       BigDecimal amountUsd,
                       BigDecimal amountEur,
                       BigDecimal rateUsdToUah,
                       BigDecimal rateEurToUah,
                       LocalDateTime timestamp) {
        this.account = account;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.balanceAfter = balanceAfter;
        this.amountUah = amountUah;
        this.amountUsd = amountUsd;
        this.amountEur = amountEur;
        this.rateUsdToUah = rateUsdToUah;
        this.rateEurToUah = rateEurToUah;
        this.timestamp = timestamp;
    }
}