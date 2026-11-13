package com.dnu.sfm.service.impl;

import com.dnu.sfm.dto.TransferHistoryEntryDto;
import com.dnu.sfm.dto.TransferResultDto;
import com.dnu.sfm.exception.AccountNotFoundException;
import com.dnu.sfm.exception.CurrencyMismatchException;
import com.dnu.sfm.exception.InactiveAccountException;
import com.dnu.sfm.model.Account;
import com.dnu.sfm.model.AccountStatus;
import com.dnu.sfm.model.CashAccount;
import com.dnu.sfm.model.DepositAccount;
import com.dnu.sfm.model.Transaction;
import com.dnu.sfm.model.TransactionType;
import com.dnu.sfm.repository.AccountRepository;
import com.dnu.sfm.repository.TransactionRepository;
import com.dnu.sfm.service.TransactionService;
import com.dnu.sfm.service.TransferService;
import com.dnu.sfm.util.TransferReferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Moves funds between accounts as a single atomic transaction.
 * <p>
 * Money can be transferred between cash accounts and from a cash account
 * to a deposit account. Every successful transfer registers a transaction
 * in both accounts: a DEBIT (money out) on the source and a CREDIT
 * (money in) on the target. Both rows share a generated transfer reference,
 * which {@link #getTransferHistory()} uses to rebuild one entry per movement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TransferServiceImpl implements TransferService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;

    @Override
    public TransferResultDto transfer(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        Objects.requireNonNull(fromAccountId, "Source account id must not be null");
        Objects.requireNonNull(toAccountId, "Target account id must not be null");
        requirePositiveAmount(amount, "Transfer amount must be positive");

        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException(
                    "Cannot transfer funds to the same account: source and target must be different.");
        }

        Account from = accountRepository.findById(fromAccountId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Source account with id " + fromAccountId + " not found"));
        Account to = accountRepository.findById(toAccountId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Target account with id " + toAccountId + " not found"));

        // Only cash accounts can send money: deposit funds are locked until
        // maturity and pay out exclusively through the close-deposit flow.
        if (!(from instanceof CashAccount)) {
            throw new IllegalArgumentException(
                    "Transfers can be sent only from cash accounts, but '"
                            + from.getName() + "' is a deposit account.");
        }

        if (from.getStatus() != AccountStatus.ACTIVE) {
            throw new InactiveAccountException(
                    "Source account '" + from.getName() + "' is " + from.getStatus()
                            + " - transfers are allowed only from ACTIVE accounts");
        }
        if (to.getStatus() != AccountStatus.ACTIVE) {
            throw new InactiveAccountException(
                    "Target account '" + to.getName() + "' is " + to.getStatus()
                            + " - transfers are allowed only to ACTIVE accounts");
        }

        if (from.getCurrency() != to.getCurrency()) {
            throw new CurrencyMismatchException(
                    "Currency mismatch: cannot transfer from '" + from.getName() + "' ("
                            + from.getCurrency() + ") to '" + to.getName() + "' (" + to.getCurrency()
                            + "). Transfers are allowed only between accounts of the same currency.");
        }

        if (from.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException(
                    "Insufficient funds: balance of '" + from.getName() + "' is " + from.getBalance()
                            + " " + from.getCurrency() + ", cannot transfer " + amount);
        }

        // Apply the movement to both balances
        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

        Account savedFrom = accountRepository.save(from);
        Account savedTo = accountRepository.save(to);

        // One reference for both legs so the transfer history page can pair them
        String transferRef = TransferReferences.generate();

        // Register the transaction in BOTH accounts: debit on the source, credit on the target
        transactionService.recordTransaction(savedFrom, TransactionType.DEBIT, amount, transferRef);
        transactionService.recordTransaction(savedTo, TransactionType.CREDIT, amount, transferRef);

        log.info("Transferred {} {} from account id={} ('{}') to account id={} ('{}'); "
                        + "new balances: {} / {}; transferRef={}",
                amount, savedFrom.getCurrency(), fromAccountId, savedFrom.getName(),
                toAccountId, savedTo.getName(), savedFrom.getBalance(), savedTo.getBalance(),
                transferRef);

        return new TransferResultDto(
                savedFrom.getId(),
                savedFrom.getName(),
                savedTo.getId(),
                savedTo.getName(),
                amount.setScale(2, RoundingMode.HALF_EVEN),
                savedFrom.getCurrency(),
                savedFrom.getBalance().setScale(2, RoundingMode.HALF_EVEN),
                savedTo.getBalance().setScale(2, RoundingMode.HALF_EVEN),
                transferRef
        );
    }

    @Override
    public List<TransferHistoryEntryDto> getTransferHistory() {
        List<Transaction> legs =
                transactionRepository.findByTransferRefIsNotNullOrderByTimestampDescIdDesc();

        // Group the legs by reference, keeping the newest-first order of the query
        Map<String, List<Transaction>> legsByRef = new LinkedHashMap<>();
        for (Transaction leg : legs) {
            legsByRef.computeIfAbsent(leg.getTransferRef(), ref -> new ArrayList<>()).add(leg);
        }

        return legsByRef.values().stream()
                .map(TransferServiceImpl::toHistoryEntry)
                .toList();
    }

    /**
     * Rebuilds one movement from its paired legs. The DEBIT row describes
     * the source account, the CREDIT row the target; the kind is derived
     * from the source - deposit accounts can only send money through the
     * close-deposit payout flow.
     */
    private static TransferHistoryEntryDto toHistoryEntry(List<Transaction> legs) {
        Transaction debit = legOfType(legs, TransactionType.DEBIT);
        Transaction credit = legOfType(legs, TransactionType.CREDIT);

        Transaction primary = debit != null ? debit : credit;

        Account from = debit != null ? debit.getAccount() : null;
        Account to = credit != null ? credit.getAccount() : null;

        TransferHistoryEntryDto.TransferKind kind =
                from instanceof DepositAccount
                        ? TransferHistoryEntryDto.TransferKind.DEPOSIT_CLOSE
                        : TransferHistoryEntryDto.TransferKind.ACCOUNT_TRANSFER;

        return new TransferHistoryEntryDto(
                primary.getTransferRef(),
                kind,
                primary.getTimestamp(),
                from != null ? from.getId() : null,
                from != null ? from.getName() : "Unknown",
                from != null ? accountTypeLabel(from) : "Unknown",
                to != null ? to.getId() : null,
                to != null ? to.getName() : "Unknown",
                to != null ? accountTypeLabel(to) : "Unknown",
                primary.getAmount(),
                primary.getCurrency()
        );
    }

    private static Transaction legOfType(List<Transaction> legs, TransactionType type) {
        return legs.stream()
                .filter(t -> t.getType() == type)
                .findFirst()
                .orElse(null);
    }

    private static String accountTypeLabel(Account account) {
        return account instanceof DepositAccount ? "Deposit" : "Cash";
    }

    private static void requirePositiveAmount(BigDecimal amount, String message) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
