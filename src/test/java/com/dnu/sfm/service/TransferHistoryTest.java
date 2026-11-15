package com.dnu.sfm.service;

import com.dnu.sfm.dto.TransferHistoryEntryDto;
import com.dnu.sfm.model.Account;
import com.dnu.sfm.model.AccountStatus;
import com.dnu.sfm.model.CashAccount;
import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.DepositAccount;
import com.dnu.sfm.model.Transaction;
import com.dnu.sfm.model.TransactionType;
import com.dnu.sfm.repository.AccountRepository;
import com.dnu.sfm.repository.TransactionRepository;
import com.dnu.sfm.service.impl.TransferServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the cross-account transfer history: the DEBIT and CREDIT
 * legs recorded by a transfer (or a deposit-close payout) must be paired
 * back into a single entry, ordered newest first, with the movement kind
 * derived from the source account.
 */
@ExtendWith(MockitoExtension.class)
class TransferHistoryTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionService transactionService;

    private TransferServiceImpl transferService;

    private CashAccount wallet;
    private CashAccount savings;
    private DepositAccount deposit;

    @BeforeEach
    void setUp() {
        transferService = new TransferServiceImpl(accountRepository, transactionRepository, transactionService);

        wallet = cashAccount(1L, "Wallet");
        savings = cashAccount(2L, "Savings");
        deposit = depositAccount(3L, "Term Deposit");
    }

    private static CashAccount cashAccount(long id, String name) {
        CashAccount account = new CashAccount();
        account.setId(id);
        account.setName(name);
        account.setBalance(new BigDecimal("1000.00"));
        account.setCurrency(Currency.USD);
        account.setStatus(AccountStatus.ACTIVE);
        account.setStartDate(LocalDate.now());
        return account;
    }

    private static DepositAccount depositAccount(long id, String name) {
        DepositAccount account = new DepositAccount(
                name, new BigDecimal("2000.00"), Currency.USD,
                new BigDecimal("5.5"), BigDecimal.ZERO,
                LocalDate.now().minusMonths(6), LocalDate.now().plusMonths(6));
        account.setId(id);
        account.setStatus(AccountStatus.ACTIVE);
        return account;
    }

    /** Builds one transfer leg as it would be persisted by TransactionServiceImp. */
    private static Transaction leg(Account account, TransactionType type, String amount,
                                   String transferRef, LocalDateTime timestamp) {
        Transaction transaction = new Transaction(
                account, type, new BigDecimal(amount), account.getCurrency(),
                new BigDecimal("100.00"), new BigDecimal("100.00"),
                new BigDecimal("100.00"), new BigDecimal("100.00"),
                new BigDecimal("41.0000"), new BigDecimal("45.0000"), timestamp);
        transaction.setTransferRef(transferRef);
        return transaction;
    }

    @Test
    void historyPairsDebitAndCreditLegsIntoOneEntry() {
        // Two movements, newest first, interleaved exactly as the repository
        // returns them (timestamp desc, id desc - the credit leg of a movement
        // is written after its debit leg, so it appears first)
        String refA = "TRF-AAAAAAAAAAAA";
        String refB = "TRF-BBBBBBBBBBBB";

        LocalDateTime timeA = LocalDateTime.of(2026, 11, 12, 9, 30);
        LocalDateTime timeB = LocalDateTime.of(2026, 11, 13, 14, 0);

        when(transactionRepository.findByTransferRefIsNotNullOrderByTimestampDescIdDesc())
                .thenReturn(List.of(
                        // movement B: cash -> deposit, newest
                        leg(savings, TransactionType.CREDIT, "300.00", refB, timeB.plusSeconds(1)),
                        leg(wallet, TransactionType.DEBIT, "300.00", refB, timeB),
                        // movement A: cash -> cash, older
                        leg(savings, TransactionType.CREDIT, "150.00", refA, timeA.plusSeconds(1)),
                        leg(wallet, TransactionType.DEBIT, "150.00", refA, timeA)));

        List<TransferHistoryEntryDto> entries = transferService.getTransferHistory();

        assertThat(entries).hasSize(2);

        // Newest movement first, legs paired correctly despite interleaving
        TransferHistoryEntryDto first = entries.get(0);
        assertThat(first.transferRef()).isEqualTo(refB);
        assertThat(first.kind()).isEqualTo(TransferHistoryEntryDto.TransferKind.ACCOUNT_TRANSFER);
        assertThat(first.fromAccountId()).isEqualTo(1L);
        assertThat(first.fromAccountName()).isEqualTo("Wallet");
        assertThat(first.fromAccountType()).isEqualTo("Cash");
        assertThat(first.toAccountId()).isEqualTo(2L);
        assertThat(first.toAccountName()).isEqualTo("Savings");
        assertThat(first.toAccountType()).isEqualTo("Cash");
        assertThat(first.amount()).isEqualByComparingTo("300.00");
        assertThat(first.currency()).isEqualTo(Currency.USD);
        assertThat(first.formattedTimestamp()).isEqualTo("2026-11-13 14:00");
        assertThat(first.amountLabel()).isEqualTo("300.00 USD");

        TransferHistoryEntryDto second = entries.get(1);
        assertThat(second.transferRef()).isEqualTo(refA);
        assertThat(second.amount()).isEqualByComparingTo("150.00");
        assertThat(second.formattedTimestamp()).isEqualTo("2026-11-12 09:30");
    }

    @Test
    void historyMarksDepositPayoutAsDepositClose() {
        // A payout can only originate from a deposit account, because deposits
        // are not allowed to be transfer sources - so a DEBIT leg on a deposit
        // identifies a deposit close
        String payoutRef = "TRF-CCCCCCCCCCCC";
        LocalDateTime payoutTime = LocalDateTime.of(2026, 11, 13, 10, 0);

        when(transactionRepository.findByTransferRefIsNotNullOrderByTimestampDescIdDesc())
                .thenReturn(List.of(
                        leg(wallet, TransactionType.CREDIT, "2114.00", payoutRef, payoutTime.plusSeconds(1)),
                        leg(deposit, TransactionType.DEBIT, "2114.00", payoutRef, payoutTime)));

        List<TransferHistoryEntryDto> entries = transferService.getTransferHistory();

        assertThat(entries).hasSize(1);
        TransferHistoryEntryDto entry = entries.get(0);
        assertThat(entry.kind()).isEqualTo(TransferHistoryEntryDto.TransferKind.DEPOSIT_CLOSE);
        assertThat(entry.kind().getLabel()).isEqualTo("Deposit close");
        assertThat(entry.fromAccountId()).isEqualTo(3L);
        assertThat(entry.fromAccountName()).isEqualTo("Term Deposit");
        assertThat(entry.fromAccountType()).isEqualTo("Deposit");
        assertThat(entry.toAccountId()).isEqualTo(1L);
        assertThat(entry.toAccountName()).isEqualTo("Wallet");
        assertThat(entry.amount()).isEqualByComparingTo("2114.00");
    }

    @Test
    void plainTransferLabelIsUsedForAccountTransfers() {
        assertThat(TransferHistoryEntryDto.TransferKind.ACCOUNT_TRANSFER.getLabel())
                .isEqualTo("Transfer");
        assertThat(TransferHistoryEntryDto.TransferKind.DEPOSIT_CLOSE.getLabel())
                .isEqualTo("Deposit close");
    }

    @Test
    void historyIsEmptyWhenNoTransferLegsExist() {
        when(transactionRepository.findByTransferRefIsNotNullOrderByTimestampDescIdDesc())
                .thenReturn(List.of());

        assertThat(transferService.getTransferHistory()).isEmpty();
    }

    @Test
    void legsMissingTheirPairStillRenderAsOneEntry() {
        // Defensive: a lone CREDIT leg (e.g. manually deleted counterpart)
        // must not break the history page
        String ref = "TRF-DDDDDDDDDDDD";
        LocalDateTime time = LocalDateTime.of(2026, 11, 10, 8, 0);

        when(transactionRepository.findByTransferRefIsNotNullOrderByTimestampDescIdDesc())
                .thenReturn(List.of(leg(wallet, TransactionType.CREDIT, "50.00", ref, time)));

        List<TransferHistoryEntryDto> entries = transferService.getTransferHistory();

        assertThat(entries).hasSize(1);
        TransferHistoryEntryDto entry = entries.get(0);
        assertThat(entry.transferRef()).isEqualTo(ref);
        assertThat(entry.fromAccountName()).isEqualTo("Unknown");
        assertThat(entry.fromAccountType()).isEqualTo("Unknown");
        assertThat(entry.toAccountId()).isEqualTo(1L);
        assertThat(entry.toAccountName()).isEqualTo("Wallet");
        assertThat(entry.kind()).isEqualTo(TransferHistoryEntryDto.TransferKind.ACCOUNT_TRANSFER);
    }
}
