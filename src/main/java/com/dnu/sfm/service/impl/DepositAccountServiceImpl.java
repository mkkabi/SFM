package com.dnu.sfm.service.impl;

import com.dnu.sfm.dto.AccountDto;
import com.dnu.sfm.dto.DepositAccountDto;
import com.dnu.sfm.model.AccountStatus;
import com.dnu.sfm.model.DepositAccount;
import com.dnu.sfm.exception.AccountNotFoundException;
import com.dnu.sfm.exception.InactiveAccountException;
import com.dnu.sfm.exception.MaturityNotReachedException;
import com.dnu.sfm.repository.AccountRepository;
import com.dnu.sfm.repository.DepositAccountRepository;
import com.dnu.sfm.service.DepositAccountService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

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
    public DepositAccount createDepositAccount(DepositAccountDto depositAccountDto) {
        DepositAccount savedAccount = depositAccountRepository.createAccount(depositAccountDto);

        log.info("Created new deposit account: id={}, name='{}', balance={} {}, interestRate={}%, maturity={}",
                savedAccount.getId(), savedAccount.getName(),
                savedAccount.getBalance(), savedAccount.getCurrency(),
                savedAccount.getInterestRate(), savedAccount.getMaturityDate());

        return savedAccount;
    }

    @Override
    public DepositAccount getDepositAccountById(Long id) {
        Objects.requireNonNull(id, "Account id must not be null");

        return depositAccountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Deposit account with id " + id + " not found"));
    }

    @Override
    public DepositAccount depositToDepositAccount(Long id, BigDecimal amount) {
        requirePositiveAmount(amount, "Deposit amount must be positive");

        DepositAccount account = getActiveDepositAccount(id);

        account.setBalance(account.getBalance().add(amount));
        DepositAccount savedAccount = depositAccountRepository.save(account);

        log.info("Deposited {} {} to deposit account id={} (new balance: {})",
                amount, savedAccount.getCurrency(), id, savedAccount.getBalance());

        return savedAccount;
    }

    @Override
    public DepositAccount withdrawFromDepositAccount(Long id, BigDecimal amount) {
        requirePositiveAmount(amount, "Withdrawal amount must be positive");

        DepositAccount account = getActiveDepositAccount(id);

        // Funds are locked until the deposit reaches its maturity date
        LocalDate today = LocalDate.now();
        if (!today.equals(account.getMaturityDate())) {
            throw new MaturityNotReachedException(
                    "Withdrawals are allowed only on the maturity date ("
                            + account.getMaturityDate() + "). Today is " + today);
        }

        if (account.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException(
                    "Insufficient funds: balance is " + account.getBalance() + " " + account.getCurrency()
                            + ", cannot withdraw " + amount);
        }

        account.setBalance(account.getBalance().subtract(amount));
        DepositAccount savedAccount = depositAccountRepository.save(account);

        log.info("Withdrew {} {} from deposit account id={} on maturity date (new balance: {})",
                amount, savedAccount.getCurrency(), id, savedAccount.getBalance());

        return savedAccount;
    }

    @Override
    public DepositAccount extendMaturityDate(Long id) {
        DepositAccount account = getDepositAccountById(id);

        long termDays = ChronoUnit.DAYS.between(account.getStartDate(), account.getMaturityDate());
        if (termDays <= 0) {
            throw new IllegalArgumentException(
                    "Cannot extend deposit '" + account.getName() + "': the term between start date ("
                            + account.getStartDate() + ") and maturity date (" + account.getMaturityDate()
                            + ") must be positive");
        }

        // new maturityDate = maturityDate + (maturityDate - startDate), i.e. the term is doubled
        LocalDate newMaturityDate = account.getMaturityDate().plusDays(termDays);
        account.setMaturityDate(newMaturityDate);
        DepositAccount savedAccount = depositAccountRepository.save(account);

        log.info("Extended maturity of deposit account id={} by {} days to {}",
                id, termDays, savedAccount.getMaturityDate());

        return savedAccount;
    }

    /**
     * Fetches the deposit account and ensures transactions are only allowed on ACTIVE accounts.
     */
    private DepositAccount getActiveDepositAccount(Long id) {
        DepositAccount account = getDepositAccountById(id);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InactiveAccountException(
                    "Deposit account '" + account.getName() + "' is " + account.getStatus()
                            + " - transactions are only allowed on ACTIVE accounts");
        }
        return account;
    }

    private static void requirePositiveAmount(BigDecimal amount, String message) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

}
