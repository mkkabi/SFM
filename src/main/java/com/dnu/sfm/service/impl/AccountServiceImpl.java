package com.dnu.sfm.service.impl;

import com.dnu.sfm.exception.AccountNotFoundException;
import com.dnu.sfm.model.Account;
import com.dnu.sfm.repository.AccountRepository;
import com.dnu.sfm.service.AccountService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;

    @Override
    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    @Override
    public Account getAccountById(Long id) {
        Objects.requireNonNull(id, "Account id must not be null");

        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Account with id " + id + " not found"));
    }
}
