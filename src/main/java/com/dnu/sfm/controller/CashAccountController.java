package com.dnu.sfm.controller;


import com.dnu.sfm.dto.AccountDto;
import com.dnu.sfm.dto.AccountDto;
import com.dnu.sfm.model.AccountType;
import com.dnu.sfm.model.Currency;
import com.dnu.sfm.service.CashAccountService;
import com.dnu.sfm.service.DepositAccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/cash-accounts")

public class CashAccountController {
    @Autowired
    private final CashAccountService cashAccountService;

    // Spring will automatically inject AccountService here
    public CashAccountController(CashAccountService cashAccountService) {
        this.cashAccountService = cashAccountService;
    }

    @GetMapping("/new")
    public String showCreateAccountForm(Model model) {
        model.addAttribute("account", new AccountDto());
        return "add-cash-account";
    }


    @PostMapping("/new")
    public String createAccount(
            @Valid @ModelAttribute("account") AccountDto accountDto,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "add-cash-account"; // or "accounts/add-account"
        }

        cashAccountService.createCashAccount(accountDto);

        // Flash attribute is automatically removed from session after the redirect renders
        redirectAttributes.addFlashAttribute("successMessage",
                "Account \"" + accountDto.name() + "\" was created successfully.");

        return "redirect:/accounts";
    }

}
