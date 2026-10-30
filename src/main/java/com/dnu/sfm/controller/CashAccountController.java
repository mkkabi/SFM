package com.dnu.sfm.controller;


import com.dnu.sfm.dto.AccountDto;
import com.dnu.sfm.exception.AccountNotFoundException;
import com.dnu.sfm.exception.InactiveAccountException;
import com.dnu.sfm.model.AccountOperation;
import com.dnu.sfm.model.AccountStatus;
import com.dnu.sfm.model.CashAccount;
import com.dnu.sfm.service.CashAccountService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/cash-accounts")

public class CashAccountController {

    private final CashAccountService cashAccountService;

    // Spring will automatically inject CashAccountService here
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
            // Re-renders the form with the submitted values and field errors
            return "add-cash-account";
        }

        cashAccountService.createCashAccount(accountDto);

        // Flash attribute is automatically removed from session after the redirect renders
        redirectAttributes.addFlashAttribute("successMessage",
                "Account \"" + accountDto.name() + "\" was created successfully.");

        return "redirect:/accounts";
    }

    /**
     * Shows the update form for a single cash account:
     * deposit / withdraw funds and change the account status.
     */
    @GetMapping("/{id}/update")
    public String showUpdateAccountForm(@PathVariable Long id, Model model) {
        CashAccount account = cashAccountService.getCashAccountById(id);
        model.addAttribute("account", account);
        return "update-cash-account";
    }

    /**
     * Applies the requested changes to the cash account:
     * an optional DEPOSIT/WITHDRAW transaction and/or a status change.
     */
    @PostMapping("/{id}/update")
    public String updateAccount(
            @PathVariable Long id,
            @RequestParam(required = false) AccountOperation operation,
            @RequestParam(required = false) BigDecimal amount,
            @RequestParam(required = false) AccountStatus status,
            RedirectAttributes redirectAttributes) {

        try {
            CashAccount account = cashAccountService.getCashAccountById(id);
            StringBuilder result = new StringBuilder();

            // 1. Balance transaction (optional)
            if (operation == AccountOperation.DEPOSIT || operation == AccountOperation.WITHDRAW) {
                if (amount == null) {
                    throw new IllegalArgumentException("Please enter an amount for the selected operation.");
                }
                if (operation == AccountOperation.DEPOSIT) {
                    cashAccountService.depositToCashAccount(id, amount);
                    result.append("Deposited ").append(amount).append(" ").append(account.getCurrency()).append(". ");
                } else {
                    cashAccountService.withdrawFromCashAccount(id, amount);
                    result.append("Withdrew ").append(amount).append(" ").append(account.getCurrency()).append(". ");
                }
            }

            // 2. Status change (optional)
            if (status != null && status != account.getStatus()) {
                cashAccountService.changeAccountStatus(id, status);
                result.append("Status changed to ").append(status).append(".");
            }

            if (result.isEmpty()) {
                redirectAttributes.addFlashAttribute("warningMessage", "No changes were made.");
            } else {
                redirectAttributes.addFlashAttribute("successMessage", result.toString().trim());
            }

            return "redirect:/accounts";

        } catch (AccountNotFoundException | InactiveAccountException | IllegalArgumentException ex) {
            // Back to the update form with the error shown next to the account data
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/cash-accounts/{id}/update";
        }
    }

}
