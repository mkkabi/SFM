package com.dnu.sfm.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Setter
@Getter
@Entity
@Table(name = "accounts")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private Currency currency = Currency.USD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    /**
     * Opening date of the account.
     */
    @Column(nullable = false)
    private LocalDate startDate;

    /**
     * Optimistic locking version: guards the read-modify-write balance updates
     * in the deposit/withdraw services against lost updates when two operations
     * touch the same account concurrently. Conflicts surface as
     * {@code OptimisticLockingFailureException}, handled by GlobalExceptionHandler.
     */
    @Version
    @Column(name = "version")
    private Long version;

    public Account() {
    }

    public Account(String name, BigDecimal balance, Currency currency) {
        this.name = name;
        this.balance = balance != null ? balance : BigDecimal.ZERO;
        this.currency = currency != null ? currency : Currency.USD;
    }

}