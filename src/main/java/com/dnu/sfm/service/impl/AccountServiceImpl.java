package com.dnu.sfm.service.impl;

import com.dnu.sfm.exception.AccountNotFoundException;
import com.dnu.sfm.exception.CurrencyMismatchException;
import com.dnu.sfm.exception.InactiveAccountException;
import com.dnu.sfm.model.Account;
import com.dnu.sfm.model.AccountStatus;
import com.dnu.sfm.repository.AccountRepository;
import com.dnu.sfm.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;

    @Override
    public Account getAccountByNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));
    }

    @Override
    public Account findById(Long id) {
        return accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException("Account not found: " + id));
    }

    @Override
    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deposit(String accountNumber, BigDecimal amount) {
        validatePositiveAmount(amount);

        Account account = getAccountForUpdate(accountNumber);
        validateAccountIsActive(account);

        account.credit(amount);
        accountRepository.save(account);

        log.info("Credited {} {} to account {}", amount, account.getCurrency(), accountNumber);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void withdraw(String accountNumber, BigDecimal amount) {
        validatePositiveAmount(amount);

        Account account = getAccountForUpdate(accountNumber);
        validateAccountIsActive(account);

        // Polymorphic call: DepositAccount automatically checks maturity/penalties
        account.debit(amount);
        accountRepository.save(account);

        log.info("Debited {} {} from account {}", amount, account.getCurrency(), accountNumber);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void transfer(String fromAccountNumber, String toAccountNumber, BigDecimal amount) {
        if (fromAccountNumber == null || toAccountNumber == null || fromAccountNumber.equalsIgnoreCase(toAccountNumber)) {
            throw new IllegalArgumentException("Source and destination account numbers must be distinct and non-null.");
        }
        validatePositiveAmount(amount);

        // Deterministic lock acquisition order avoids database deadlocks during concurrent reciprocal transfers
        boolean isFirstSmaller = fromAccountNumber.compareTo(toAccountNumber) < 0;
        String firstKey = isFirstSmaller ? fromAccountNumber : toAccountNumber;
        String secondKey = isFirstSmaller ? toAccountNumber : fromAccountNumber;

        Account first = getAccountForUpdate(firstKey);
        Account second = getAccountForUpdate(secondKey);

        Account source = fromAccountNumber.equals(first.getAccountNumber()) ? first : second;
        Account destination = toAccountNumber.equals(first.getAccountNumber()) ? first : second;

        validateAccountIsActive(source);
        validateAccountIsActive(destination);

        if (!source.getCurrency().equalsIgnoreCase(destination.getCurrency())) {
            throw new CurrencyMismatchException(String.format(
                    "Cross-currency transfer from %s to %s requires foreign exchange conversion.",
                    source.getCurrency(), destination.getCurrency()
            ));
        }

        // Debit verifies sufficient funds (and early maturity locks/penalties if source is DepositAccount)
        source.debit(amount);
        destination.credit(amount);

        accountRepository.save(source);
        accountRepository.save(destination);

        log.info("Transferred {} {} from {} to {}", amount, source.getCurrency(), fromAccountNumber, toAccountNumber);
    }

    @Override
    @Transactional
    public void updateAccountStatus(String accountNumber, AccountStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("Target account status cannot be null.");
        }

        Account account = getAccountForUpdate(accountNumber);

        if (newStatus == AccountStatus.CLOSED) {
            ensureEligibleForClosure(account);
        }

        account.setStatus(newStatus);
        accountRepository.save(account);

        log.info("Status of account {} updated to {}", accountNumber, newStatus);
    }

    @Override
    @Transactional
    public void closeAccount(String accountNumber) {
        updateAccountStatus(accountNumber, AccountStatus.CLOSED);
    }

    // --- Internal Invariant & Lock Helpers ---

    private Account getAccountForUpdate(String accountNumber) {
        return accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));
    }

    private void validateAccountIsActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InactiveAccountException(String.format(
                    "Account %s cannot perform operations in status: %s",
                    account.getAccountNumber(), account.getStatus()
            ));
        }
    }

    private void ensureEligibleForClosure(Account account) {
        if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException(String.format(
                    "Cannot close account %s: positive balance remaining (%s %s).",
                    account.getAccountNumber(), account.getBalance(), account.getCurrency()
            ));
        }
    }

    private void validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be strictly positive.");
        }
    }
}