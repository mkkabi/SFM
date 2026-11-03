package com.dnu.sfm.service.impl;

import com.dnu.sfm.dto.DepositSettlementDto;
import com.dnu.sfm.dto.DepositSettlementDto.TrancheInterestBreakdown;
import com.dnu.sfm.model.DepositAccount;
import com.dnu.sfm.model.Transaction;
import com.dnu.sfm.model.TransactionType;
import com.dnu.sfm.repository.DepositAccountRepository;
import com.dnu.sfm.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DepositInterestCalculatorService {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(365);
    private static final BigDecimal TOTAL_TAX_RATE = new BigDecimal("0.08"); // 5% + 3%

    private final DepositAccountRepository depositAccountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public DepositSettlementDto calculateSettlement(Long depositAccountId) {
        DepositAccount account = depositAccountRepository.findById(depositAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Deposit account not found: " + depositAccountId));

        LocalDate maturityDate = account.getMaturityDate();
        BigDecimal annualRate = account.getInterestRate().divide(HUNDRED, MC);

        // Fetch all transactions for this deposit
        List<Transaction> transactions = transactionRepository.findByAccountIdOrderByTimestampDescIdDesc(depositAccountId);

        BigDecimal totalGrossInterest = BigDecimal.ZERO;
        List<TrancheInterestBreakdown> breakdown = new ArrayList<>();

        for (Transaction tx : transactions) {
            if (tx.getType() == TransactionType.DEPOSIT) {
                LocalDate depositDate = tx.getTimestamp().toLocalDate();

                // If deposited on or after maturity, no interest is earned
                long daysHeld = ChronoUnit.DAYS.between(depositDate, maturityDate);
                if (daysHeld > 0) {
                    BigDecimal daysRatio = BigDecimal.valueOf(daysHeld).divide(DAYS_IN_YEAR, MC);
                    BigDecimal trancheInterest = tx.getAmount()
                            .multiply(annualRate, MC)
                            .multiply(daysRatio, MC);

                    totalGrossInterest = totalGrossInterest.add(trancheInterest);
                    breakdown.add(new TrancheInterestBreakdown(
                            tx.getAmount().setScale(2, RoundingMode.HALF_EVEN),
                            daysHeld,
                            trancheInterest.setScale(2, RoundingMode.HALF_EVEN)
                    ));
                } else {
                    breakdown.add(new TrancheInterestBreakdown(
                            tx.getAmount().setScale(2, RoundingMode.HALF_EVEN),
                            0,
                            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN)
                    ));
                }
            }
        }

        BigDecimal totalPrincipal = account.getBalance();
        BigDecimal taxWithheld = totalGrossInterest.multiply(TOTAL_TAX_RATE, MC);
        BigDecimal netInterest = totalGrossInterest.subtract(taxWithheld, MC);
        BigDecimal finalPayout = totalPrincipal.add(netInterest, MC);

        return new DepositSettlementDto(
                account.getId(),
                totalPrincipal.setScale(2, RoundingMode.HALF_EVEN),
                totalGrossInterest.setScale(2, RoundingMode.HALF_EVEN),
                taxWithheld.setScale(2, RoundingMode.HALF_EVEN),
                netInterest.setScale(2, RoundingMode.HALF_EVEN),
                finalPayout.setScale(2, RoundingMode.HALF_EVEN),
                breakdown
        );
    }
}