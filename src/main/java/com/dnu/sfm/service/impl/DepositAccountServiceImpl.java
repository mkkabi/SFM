package com.dnu.sfm.service.impl;

import com.dnu.sfm.dto.AccountDto;
import com.dnu.sfm.dto.DepositAccountDto;
import com.dnu.sfm.dto.DepositCloseResultDto;
import com.dnu.sfm.dto.DepositSettlementDto;
import com.dnu.sfm.model.Account;
import com.dnu.sfm.model.AccountStatus;
import com.dnu.sfm.model.CashAccount;
import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.DepositAccount;
import com.dnu.sfm.model.TransactionType;
import com.dnu.sfm.exception.AccountNotFoundException;
import com.dnu.sfm.exception.CurrencyMismatchException;
import com.dnu.sfm.exception.InactiveAccountException;
import com.dnu.sfm.exception.MaturityNotReachedException;
import com.dnu.sfm.repository.AccountRepository;
import com.dnu.sfm.repository.DepositAccountRepository;
import com.dnu.sfm.service.DepositAccountService;
import com.dnu.sfm.service.TransactionService;
import com.dnu.sfm.util.TransferReferences;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final TransactionService transactionService;
    private final DepositInterestCalculatorService depositInterestCalculatorService;



    @Override
    public DepositAccount createDepositAccount(DepositAccountDto depositAccountDto) {
        Objects.requireNonNull(depositAccountDto, "Deposit account DTO must not be null");

        // 1. Persist the deposit account entity
        DepositAccount savedAccount = depositAccountRepository.createAccount(depositAccountDto);

        // 2. If an opening balance was provided, record it as the initial DEPOSIT transaction
        if (savedAccount.getBalance() != null && savedAccount.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            transactionService.recordTransaction(
                    savedAccount,
                    TransactionType.DEPOSIT,
                    savedAccount.getBalance(),
                    savedAccount.getStartDate().atStartOfDay()
            );
        }

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
    @CacheEvict(value = "depositSettlements", key = "#id")
    public DepositAccount depositToDepositAccount(Long id, BigDecimal amount) {
        requirePositiveAmount(amount, "Deposit amount must be positive");

        DepositAccount account = getActiveDepositAccount(id);

        account.setBalance(account.getBalance().add(amount));
        DepositAccount savedAccount = depositAccountRepository.save(account);

        // Track the transaction so the account history stays complete
        transactionService.recordTransaction(savedAccount, TransactionType.DEPOSIT, amount);

        log.info("Deposited {} {} to deposit account id={} (new balance: {})",
                amount, savedAccount.getCurrency(), id, savedAccount.getBalance());

        return savedAccount;
    }

    @Override
    @CacheEvict(value = "depositSettlements", key = "#id")
    public DepositAccount withdrawFromDepositAccount(Long id, BigDecimal amount) {
        requirePositiveAmount(amount, "Withdrawal amount must be positive");

        DepositAccount account = getActiveDepositAccount(id);

        // Funds are locked until the deposit reaches its maturity date; they stay
        // reachable afterwards (a strict equality check would brick the deposit
        // if the maturity day passes without a withdrawal).
        LocalDate today = LocalDate.now();
        if (today.isBefore(account.getMaturityDate())) {
            throw new MaturityNotReachedException(
                    "Withdrawals are allowed only on or after the maturity date ("
                            + account.getMaturityDate() + "). Today is " + today);
        }

        if (account.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException(
                    "Insufficient funds: balance is " + account.getBalance() + " " + account.getCurrency()
                            + ", cannot withdraw " + amount);
        }

        account.setBalance(account.getBalance().subtract(amount));
        DepositAccount savedAccount = depositAccountRepository.save(account);

        // Track the transaction so the account history stays complete
        transactionService.recordTransaction(savedAccount, TransactionType.WITHDRAWAL, amount);

        log.info("Withdrew {} {} from deposit account id={} on maturity date (new balance: {})",
                amount, savedAccount.getCurrency(), id, savedAccount.getBalance());

        return savedAccount;
    }

    @Override
    @CacheEvict(value = "depositSettlements", key = "#id")
    public DepositCloseResultDto closeDepositAccountAndTransfer(Long id, Long targetAccountId) {
        Objects.requireNonNull(targetAccountId, "Target account id must not be null");

        DepositAccount account = getActiveDepositAccount(id);

        // The deposit is locked until its maturity date has passed; from that
        // day on the user may close it and withdraw the full payout.
        LocalDate today = LocalDate.now();
        if (today.isBefore(account.getMaturityDate())) {
            throw new MaturityNotReachedException(
                    "The deposit '" + account.getName() + "' can be closed and withdrawn only after "
                            + "the maturity date (" + account.getMaturityDate() + "). Today is " + today);
        }

        if (targetAccountId.equals(account.getId())) {
            throw new IllegalArgumentException(
                    "Cannot pay out a deposit into itself: choose another account to receive the funds.");
        }

        Account target = accountRepository.findById(targetAccountId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Target account with id " + targetAccountId + " not found"));

        // The payout must land on a cash account the user controls
        if (!(target instanceof CashAccount)) {
            throw new IllegalArgumentException(
                    "The payout must be transferred to a cash account, but '"
                            + target.getName() + "' is a deposit account.");
        }

        if (target.getStatus() != AccountStatus.ACTIVE) {
            throw new InactiveAccountException(
                    "Target account '" + target.getName() + "' is " + target.getStatus()
                            + " - funds can be transferred only to ACTIVE accounts");
        }

        if (target.getCurrency() != account.getCurrency()) {
            throw new CurrencyMismatchException(
                    "Currency mismatch: the deposit is in " + account.getCurrency()
                            + " but the target account is in " + target.getCurrency()
                            + ". Funds can be transferred only between accounts of the same currency.");
        }

        // Payout = principal (current balance) + net interest after tax,
        // as computed by the settlement calculator.
        DepositSettlementDto settlement = depositInterestCalculatorService.calculateSettlement(id);
        BigDecimal principal = account.getBalance();
        BigDecimal netInterest = settlement.netInterest() != null
                ? settlement.netInterest() : BigDecimal.ZERO;
        BigDecimal payout = principal.add(netInterest).setScale(2, RoundingMode.HALF_EVEN);

        // Empty and close the deposit: the balance held the principal, the
        // interest was tracked on top of it, so after the payout the
        // remaining balance is exactly zero.
        account.setBalance(BigDecimal.ZERO);
        account.setStatus(AccountStatus.CLOSED);
        account.setAccruedInterest(BigDecimal.ZERO);
        DepositAccount savedDeposit = depositAccountRepository.save(account);

        // Credit the payout to the selected target account
        target.setBalance(target.getBalance().add(payout));
        Account savedTarget = accountRepository.save(target);

        // One reference for both legs so the payout shows up on the transfer history page
        String transferRef = TransferReferences.generate();

        // Register the transaction in BOTH accounts: debit (outgoing) on the
        // deposit and credit (incoming) on the target
        transactionService.recordTransaction(savedDeposit, TransactionType.DEBIT, payout, transferRef);
        transactionService.recordTransaction(savedTarget, TransactionType.CREDIT, payout, transferRef);

        log.info("Closed deposit account id={} ('{}'): payout {} {} (principal {} + net interest {}) "
                        + "transferred to account id={} ('{}'); deposit status=CLOSED; transferRef={}",
                id, savedDeposit.getName(), payout, savedDeposit.getCurrency(),
                principal, netInterest, targetAccountId, savedTarget.getName(), transferRef);

        return new DepositCloseResultDto(
                savedDeposit.getId(),
                savedDeposit.getName(),
                principal.setScale(2, RoundingMode.HALF_EVEN),
                netInterest.setScale(2, RoundingMode.HALF_EVEN),
                payout,
                savedDeposit.getCurrency(),
                savedTarget.getId(),
                savedTarget.getName()
        );
    }

    @Override
    @CacheEvict(value = "depositSettlements", key = "#id")
    public DepositAccount extendMaturityDate(Long id) {
        DepositAccount account = getDepositAccountById(id);

        long termDays = ChronoUnit.DAYS.between(account.getStartDate(), account.getMaturityDate());
        if (termDays <= 0) {
            throw new IllegalArgumentException(
                    "Cannot extend deposit '" + account.getName() + "': the term between start date ("
                            + account.getStartDate() + ") and maturity date (" + account.getMaturityDate()
                            + ") must be positive");
        }

        // new maturityDate = maturityDate + (maturityDate - startDate), i.e. the term is doubled.
        // Not a money movement - the balance does not change, so no Transaction is recorded.
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
