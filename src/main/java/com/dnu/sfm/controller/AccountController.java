package com.dnu.sfm.controller;


import com.dnu.sfm.model.Account;
import com.dnu.sfm.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/accounts")
public class AccountController {

    @Autowired
    private final AccountService accountService;

    // Spring will automatically inject AccountService here
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Lists all accounts (cash and deposit).
     * Handles GET /accounts - the target of the redirect after account creation.
     */
    @GetMapping
    public String findAllAccounts(Model model) {
        List<Account> accounts = accountService.getAllAccounts();
        model.addAttribute("accounts", accounts);
        return "accounts";
    }
}
