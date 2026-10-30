package com.dnu.sfm.service.impl;

import com.dnu.sfm.dto.AccountDto;
import com.dnu.sfm.model.AccountStatus;
import com.dnu.sfm.model.CashAccount;
import com.dnu.sfm.model.Currency;
import com.dnu.sfm.repository.AccountRepository;
import com.dnu.sfm.service.CashAccountService;
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
}
