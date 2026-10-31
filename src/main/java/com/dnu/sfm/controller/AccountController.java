package com.dnu.sfm.controller;


import com.dnu.sfm.dto.AccountCurrencyComparisonDto;
import com.dnu.sfm.model.Account;
import com.dnu.sfm.service.AccountService;
import com.dnu.sfm.service.CurrencyAnalyticsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;
    private final CurrencyAnalyticsService currencyAnalyticsService;

    // Spring will automatically inject AccountService here
    public AccountController(AccountService accountService, CurrencyAnalyticsService currencyAnalyticsService) {
        this.accountService = accountService;
        this.currencyAnalyticsService = currencyAnalyticsService;
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

    @GetMapping("/{id}/analytics/currency-comparison")
    public String showCurrencyComparison(@PathVariable Long id, Model model) {
        AccountCurrencyComparisonDto comparison = currencyAnalyticsService.compareCurrencyOutcomes(id);
        model.addAttribute("comparison", comparison);
        return "currency-comparison";
    }
}
