package com.dnu.sfm.service.impl;

import com.dnu.sfm.model.Account;
import com.dnu.sfm.service.AccountService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountServiceImpl implements AccountService {
    @Override
    public List<Account> getAllAccounts() {
        return List.of();
    }
}
