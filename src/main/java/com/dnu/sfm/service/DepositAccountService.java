package com.dnu.sfm.service;

import com.dnu.sfm.model.DepositAccount;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface DepositAccountService {
    DepositAccount createDepositAccount(
            String accountNumber,
            String name,
            BigDecimal initialDeposit,
            String currency,
            BigDecimal interestRate,
            LocalDate startDate,
            LocalDate maturityDate,
            boolean earlyWithdrawalAllowed,
            BigDecimal penaltyRate
    );

    void accrueDailyInterest(String accountNumber);
    void capitalizeInterest(String accountNumber);
}