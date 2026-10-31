package com.dnu.sfm.repository;

import com.dnu.sfm.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * All transactions of one account, newest first
     * (id as tie-breaker when two transactions share the same timestamp).
     */
    List<Transaction> findByAccountIdOrderByTimestampDescIdDesc(Long accountId);

}
