package com.dnu.sfm.repository;

import com.dnu.sfm.dto.DepositAccountDto;
import com.dnu.sfm.model.DepositAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Repository
public interface DepositAccountRepository extends JpaRepository<DepositAccount, Long>, JpaSpecificationExecutor<DepositAccount> {


    @Transactional
    default DepositAccount createAccount(DepositAccountDto depositAccountDto) {
        Objects.requireNonNull(depositAccountDto, "Deposit account DTO must not be null");

        return null;
    }


}