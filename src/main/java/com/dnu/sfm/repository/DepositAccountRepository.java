package com.dnu.sfm.repository;


import com.dnu.sfm.model.AccountStatus;
import com.dnu.sfm.model.DepositAccount;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DepositAccountRepository extends JpaRepository<DepositAccount, Long>, JpaSpecificationExecutor<DepositAccount> {

    /**
     * Finds an account by its unique public account number.
     */
    Optional<DepositAccount> findByAccountNumber(String accountNumber);

    /**
     * Checks if an account number already exists (useful for collision checking during generation).
     */
    boolean existsByAccountNumber(String accountNumber);

    /**
     * Retrieves an account by account number with a pessimistic write lock.
     * Essential for financial transactions (deposits, early withdrawals, closures)
     * to eliminate race conditions and dirty reads.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000")})
    @Query("SELECT d FROM DepositAccount d WHERE d.accountNumber = :accountNumber")
    Optional<DepositAccount> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);

    /**
     * Retrieves an account by internal ID with a pessimistic write lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000")})
    @Query("SELECT d FROM DepositAccount d WHERE d.id = :id")
    Optional<DepositAccount> findByIdForUpdate(@Param("id") Long id);

    /**
     * Retrieves all accounts associated with a specific user, paginated.
     */
    Page<DepositAccount> findByUserId(Long userId, Pageable pageable);

    /**
     * Retrieves accounts associated with a specific user filtered by status.
     */
    List<DepositAccount> findByUserIdAndStatus(Long userId, AccountStatus status);

    /**
     * Finds active accounts due for maturity processing up to a specific cutoff date.
     * Used by end-of-day (EOD) settlement batch jobs.
     */
    @Query("SELECT d FROM DepositAccount d " +
            "WHERE d.status = :status " +
            "AND d.maturityDate <= :cutoffDate")
    List<DepositAccount> findMaturedAccounts(
            @Param("status") AccountStatus status,
            @Param("cutoffDate") LocalDate cutoffDate
    );

    /**
     * Batch query for daily/monthly interest accrual calculation.
     * Fetches paginated active deposit accounts to prevent memory exhaustion during bulk jobs.
     */
    @Query("SELECT d FROM DepositAccount d " +
            "WHERE d.status = 'ACTIVE' " +
            "AND d.balance > 0")
    Page<DepositAccount> findActiveAccountsForInterestAccrual(Pageable pageable);

    /**
     * Counts active accounts owned by a specific user (enforcing account quota limits).
     */
    long countByUserIdAndStatus(Long userId, AccountStatus status);
}