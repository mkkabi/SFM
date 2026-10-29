package com.dnu.sfm.service.impl;

import com.dnu.sfm.dto.AccountDto;
import com.dnu.sfm.dto.DepositAccountDto;
import com.dnu.sfm.exception.AccountNotFoundException;
import com.dnu.sfm.model.DepositAccount;
import com.dnu.sfm.repository.AccountRepository;
import com.dnu.sfm.repository.DepositAccountRepository;
import com.dnu.sfm.service.DepositAccountService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DepositAccountServiceImpl implements DepositAccountService {

    /**
     * Fallback term applied to form-created deposits, which carry no explicit
     * maturity information in {@link AccountDto}.
     */
    private static final int DEFAULT_TERM_MONTHS = 12;

    /** Length of the random suffix of generated account numbers. */
    private static final int GENERATED_SUFFIX_LENGTH = 8;

    /** Prefix marking an auto-generated deposit account number. */
    private static final String GENERATED_PREFIX = "DEP-";

    private final AccountRepository accountRepository;
    private final DepositAccountRepository depositAccountRepository;

    @Override
    public DepositAccount createDepositAccount(AccountDto accountDto) {
        Objects.requireNonNull(accountDto, "Account DTO must not be null");

        String name = requireText(accountDto.name(), "Account name is required");
        String currency = normalizeCurrency(accountDto.currency());

        BigDecimal openingBalance = accountDto.initialBalance() != null
                ? accountDto.initialBalance()
                : BigDecimal.ZERO;
        if (openingBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative: " + openingBalance);
        }

        String accountNumber = resolveUniqueAccountNumber(accountDto.accountNumber());

        // The simplified form carries no deposit terms, so the account opens as a
        // non-interest-bearing deposit with a standard 12-month term. Early
        // withdrawal stays enabled (with no penalty) so funds are not locked away
        // until the business configures real interest conditions.
        LocalDate today = LocalDate.now();
        DepositAccount account = new DepositAccount(
                accountNumber,
                name,
                openingBalance,
                currency,
                BigDecimal.ZERO,                    // interestRate: non-interest-bearing until configured
                today,                              // startDate
                today.plusMonths(DEFAULT_TERM_MONTHS), // maturityDate (must be after startDate)
                true,                               // earlyWithdrawalAllowed
                BigDecimal.ZERO                     // penaltyRate
        );

        DepositAccount saved = depositAccountRepository.save(account);
        log.info("Created deposit account {} ({}) with opening balance {} {}",
                saved.getAccountNumber(), name, saved.getBalance(), saved.getCurrency());
        return saved;
    }

    @Override
    public DepositAccount createDepositAccount(DepositAccountDto depositAccountDto) {
        // Mapping, duplicate-account-number checking and persistence are
        // handled by DepositAccountRepository#createAccount(DepositAccountDto).
        return depositAccountRepository.createAccount(depositAccountDto);
    }

    @Override
    public void accrueDailyInterest(String accountNumber) {
        DepositAccount account = (DepositAccount) accountRepository.findByAccountNumberForUpdate(accountNumber)
                .filter(a -> a instanceof DepositAccount)
                .orElseThrow(() -> new AccountNotFoundException("Deposit account not found: " + accountNumber));

        account.accrueDailyInterest();
        accountRepository.save(account);
    }

    @Override
    public void capitalizeInterest(String accountNumber) {
        DepositAccount account = (DepositAccount) accountRepository.findByAccountNumberForUpdate(accountNumber)
                .filter(a -> a instanceof DepositAccount)
                .orElseThrow(() -> new AccountNotFoundException("Deposit account not found: " + accountNumber));

        account.capitalizeInterest();
        accountRepository.save(account);
    }

    // --- Internal Helpers ---

    /**
     * Uses the caller-supplied account number when present (rejecting
     * duplicates against the full account table, since {@code account_number}
     * is globally unique), otherwise generates a collision-free identifier.
     */
    private String resolveUniqueAccountNumber(String requestedAccountNumber) {
        if (requestedAccountNumber != null && !requestedAccountNumber.isBlank()) {
            String candidate = requestedAccountNumber.trim();
            if (accountRepository.existsByAccountNumber(candidate)) {
                throw new IllegalArgumentException("Account number already exists: " + candidate);
            }
            return candidate;
        }

        String generated;
        do {
            generated = GENERATED_PREFIX + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, GENERATED_SUFFIX_LENGTH)
                    .toUpperCase();
        } while (accountRepository.existsByAccountNumber(generated));
        return generated;
    }

    /**
     * Normalizes the currency code to upper-case ISO 4217 form, defaulting to
     * USD when absent (consistent with {@code CashAccountServiceImpl}).
     */
    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "USD";
        }
        String normalized = currency.trim().toUpperCase();
        if (!normalized.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("Currency must adhere to ISO 4217 standard: " + currency);
        }
        return normalized;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
