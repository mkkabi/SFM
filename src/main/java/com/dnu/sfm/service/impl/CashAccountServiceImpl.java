package com.dnu.sfm.service.impl;

import com.dnu.sfm.dto.AccountDto;
import com.dnu.sfm.model.AccountStatus;
import com.dnu.sfm.model.CashAccount;
import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.TransactionType;
import com.dnu.sfm.exception.AccountNotFoundException;
import com.dnu.sfm.exception.InactiveAccountException;
import com.dnu.sfm.repository.AccountRepository;
import com.dnu.sfm.service.CashAccountService;
import com.dnu.sfm.service.TransactionService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CashAccountServiceImpl implements CashAccountService {

    private final AccountRepository accountRepository;
    private final TransactionService transactionService;


    @Override
    public CashAccount createCashAccount(AccountDto accountDto) {
        Objects.requireNonNull(accountDto, "Account DTO must not be null");

        // Map the validated DTO onto a new CashAccount entity
        CashAccount account = new CashAccount();
        account.setName(accountDto.name());
        account.setBalance(accountDto.balance() != null ? accountDto.balance() : BigDecimal.ZERO);
        account.setCurrency(accountDto.currency() != null ? accountDto.currency() : Currency.USD);
        account.setStatus(accountDto.status() != null ? accountDto.status() : AccountStatus.ACTIVE);
        account.setStartDate(accountDto.startDate() != null ? accountDto.startDate() : LocalDate.now());

        CashAccount savedAccount = accountRepository.save(account);

        log.info("Created new cash account: id={}, name='{}', balance={} {}",
                savedAccount.getId(), savedAccount.getName(),
                savedAccount.getBalance(), savedAccount.getCurrency());

        return savedAccount;
    }

    @Override
    public CashAccount getCashAccountById(Long id) {
        Objects.requireNonNull(id, "Account id must not be null");

        return accountRepository.findById(id)
                .filter(CashAccount.class::isInstance)
                .map(CashAccount.class::cast)
                .orElseThrow(() -> new AccountNotFoundException("Cash account with id " + id + " not found"));
    }

    @Override
    public CashAccount depositToCashAccount(Long id, BigDecimal amount) {
        requirePositiveAmount(amount, "Deposit amount must be positive");

        CashAccount account = getActiveCashAccount(id);

        account.setBalance(account.getBalance().add(amount));
        CashAccount savedAccount = accountRepository.save(account);

        // Track the transaction so the account history stays complete
        transactionService.recordTransaction(savedAccount, TransactionType.DEPOSIT, amount);

        log.info("Deposited {} {} to cash account id={} (new balance: {})",
                amount, savedAccount.getCurrency(), id, savedAccount.getBalance());

        return savedAccount;
    }

    @Override
    public CashAccount withdrawFromCashAccount(Long id, BigDecimal amount) {
        requirePositiveAmount(amount, "Withdrawal amount must be positive");

        CashAccount account = getActiveCashAccount(id);

        if (account.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException(
                    "Insufficient funds: balance is " + account.getBalance() + " " + account.getCurrency()
                            + ", cannot withdraw " + amount);
        }

        account.setBalance(account.getBalance().subtract(amount));
        CashAccount savedAccount = accountRepository.save(account);

        // Track the transaction so the account history stays complete
        transactionService.recordTransaction(savedAccount, TransactionType.WITHDRAWAL, amount);

        log.info("Withdrew {} {} from cash account id={} (new balance: {})",
                amount, savedAccount.getCurrency(), id, savedAccount.getBalance());

        return savedAccount;
    }

    @Override
    public CashAccount changeAccountStatus(Long id, AccountStatus newStatus) {
        Objects.requireNonNull(newStatus, "New status must not be null");

        CashAccount account = getCashAccountById(id);

        if (account.getStatus() == newStatus) {
            return account; // nothing to change
        }

        AccountStatus oldStatus = account.getStatus();
        account.setStatus(newStatus);
        CashAccount savedAccount = accountRepository.save(account);

        log.info("Changed status of cash account id={} from {} to {}",
                id, oldStatus, newStatus);

        return savedAccount;
    }

    /**
     * Fetches the cash account and ensures transactions are only allowed on ACTIVE accounts.
     */
    private CashAccount getActiveCashAccount(Long id) {
        CashAccount account = getCashAccountById(id);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InactiveAccountException(
                    "Cash account '" + account.getName() + "' is " + account.getStatus()
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
