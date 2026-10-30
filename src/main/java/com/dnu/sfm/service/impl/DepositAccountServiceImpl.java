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
    public DepositAccount createDepositAccount(DepositAccountDto depositAccountDto) {
        return depositAccountRepository.createAccount(depositAccountDto);
    }

}
