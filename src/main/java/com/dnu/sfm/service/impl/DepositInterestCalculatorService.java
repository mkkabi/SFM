package com.dnu.sfm.service.impl;

import com.dnu.sfm.dto.DepositSettlementDto;
import com.dnu.sfm.dto.DepositSettlementDto.TrancheBreakdownDto;
import com.dnu.sfm.exception.AccountNotFoundException;
import com.dnu.sfm.model.DepositAccount;
import com.dnu.sfm.model.Transaction;
import com.dnu.sfm.model.TransactionType;
import com.dnu.sfm.repository.DepositAccountRepository;
import com.dnu.sfm.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DepositInterestCalculatorService {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(365);

    private final DepositAccountRepository depositAccountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    @Cacheable(value = "depositSettlements", key = "#accountId")
    public DepositSettlementDto calculateSettlement(Long accountId) {
        DepositAccount account = depositAccountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Deposit account with id " + accountId + " not found"));

        LocalDate maturityDate = account.getMaturityDate();
        LocalDate startDate = account.getStartDate();
        long totalTermDays = ChronoUnit.DAYS.between(startDate, maturityDate);

        // Interest rate and tax rate fractions from the account model
        BigDecimal annualRateFraction = account.getInterestRate().divide(HUNDRED, MC);
        BigDecimal taxRateFraction = (account.getTaxRate() != null ? account.getTaxRate() : BigDecimal.ZERO)
                .divide(HUNDRED, MC);

        List<Transaction> transactions = transactionRepository.findByAccountIdOrderByTimestampDescIdDesc(accountId)
                .stream()
                .sorted(Comparator.comparing(Transaction::getTimestamp).thenComparing(Transaction::getId))
                .toList();

        BigDecimal totalGrossInterest = BigDecimal.ZERO;
        List<TrancheBreakdownDto> tranches = new ArrayList<>();
        boolean isFirstDeposit = true;

        for (Transaction tx : transactions) {
            if (tx.getType() == TransactionType.DEPOSIT) {
                LocalDate depositDate = tx.getTimestamp().toLocalDate();
                long daysInvested = ChronoUnit.DAYS.between(depositDate, maturityDate);

                BigDecimal trancheGross;
                if (daysInvested > 0) {
                    BigDecimal daysRatio = BigDecimal.valueOf(daysInvested).divide(DAYS_IN_YEAR, MC);
                    trancheGross = tx.getAmount()
                            .multiply(annualRateFraction, MC)
                            .multiply(daysRatio, MC);
                } else {
                    daysInvested = 0;
                    trancheGross = BigDecimal.ZERO;
                }

                // Dynamic tax per tranche
                BigDecimal trancheTax = trancheGross.multiply(taxRateFraction, MC);
                BigDecimal trancheNet = trancheGross.subtract(trancheTax, MC);

                totalGrossInterest = totalGrossInterest.add(trancheGross);

                tranches.add(new TrancheBreakdownDto(
                        depositDate,
                        isFirstDeposit,
                        tx.getAmount().setScale(2, RoundingMode.HALF_EVEN),
                        daysInvested,
                        trancheGross.setScale(2, RoundingMode.HALF_EVEN),
                        trancheNet.setScale(2, RoundingMode.HALF_EVEN)
                ));

                isFirstDeposit = false;
            }
        }

        BigDecimal totalPrincipal = account.getBalance();
        BigDecimal totalTaxWithheld = totalGrossInterest.multiply(taxRateFraction, MC);
        BigDecimal totalNetInterest = totalGrossInterest.subtract(totalTaxWithheld, MC);
        BigDecimal finalPayout = totalPrincipal.add(totalNetInterest, MC);

        return new DepositSettlementDto(
                account.getId(),
                account.getName(),
                account.getCurrency().name(),
                account.getInterestRate().setScale(4, RoundingMode.HALF_EVEN),
                (account.getTaxRate() != null ? account.getTaxRate() : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN),
                startDate,
                maturityDate,
                totalTermDays,
                totalPrincipal.setScale(2, RoundingMode.HALF_EVEN),
                totalGrossInterest.setScale(2, RoundingMode.HALF_EVEN),
                totalTaxWithheld.setScale(2, RoundingMode.HALF_EVEN),
                totalNetInterest.setScale(2, RoundingMode.HALF_EVEN),
                finalPayout.setScale(2, RoundingMode.HALF_EVEN),
                tranches
        );
    }
}