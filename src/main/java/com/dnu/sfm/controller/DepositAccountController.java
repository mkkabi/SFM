package com.dnu.sfm.controller;

import com.dnu.sfm.dto.DepositAccountDto;
import com.dnu.sfm.exception.AccountNotFoundException;
import com.dnu.sfm.exception.InactiveAccountException;
import com.dnu.sfm.exception.MaturityNotReachedException;
import com.dnu.sfm.model.AccountOperation;
import com.dnu.sfm.model.DepositAccount;
import com.dnu.sfm.service.DepositAccountService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/deposit-accounts")
public class DepositAccountController {

    private final DepositAccountService depositAccountService;

    // Spring will automatically inject DepositAccountService here
    public DepositAccountController(DepositAccountService depositAccountService) {
        this.depositAccountService = depositAccountService;
    }

    @GetMapping("/new")
    public String showCreateDepositForm(Model model) {
        model.addAttribute("account", new DepositAccountDto());
        return "add-deposit-account";
    }

    @PostMapping("/new")
    public String createAccount(
            @Valid @ModelAttribute("account") DepositAccountDto accountDto,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            // Re-renders the form with the submitted values and field errors
            return "add-deposit-account";
        }

        depositAccountService.createDepositAccount(accountDto);

        // Flash attribute is automatically removed from session after the redirect renders
        redirectAttributes.addFlashAttribute("successMessage",
                "Deposit account \"" + accountDto.name() + "\" was created successfully.");

        return "redirect:/accounts";
    }

    /**
     * Shows the update form for a single deposit account:
     * deposit / withdraw funds and extend the maturity date.
     */
    @GetMapping("/{id}/update")
    public String showUpdateAccountForm(@PathVariable Long id, Model model) {
        DepositAccount account = depositAccountService.getDepositAccountById(id);
        model.addAttribute("account", account);
        return "update-deposit-account";
    }

    /**
     * Applies the requested change to the deposit account:
     * a DEPOSIT/WITHDRAW transaction or an EXTEND_MATURITY operation.
     * Withdrawals are only possible on the maturity date of the deposit.
     */
    @PostMapping("/{id}/update")
    public String updateAccount(
            @PathVariable Long id,
            @RequestParam AccountOperation operation,
            @RequestParam(required = false) BigDecimal amount,
            RedirectAttributes redirectAttributes) {

        try {
            DepositAccount account = depositAccountService.getDepositAccountById(id);
            String result;

            switch (operation) {
                case DEPOSIT -> {
                    depositAccountService.depositToDepositAccount(id, amount);
                    result = "Deposited " + amount + " " + account.getCurrency() + ".";
                }
                case WITHDRAW -> {
                    depositAccountService.withdrawFromDepositAccount(id, amount);
                    result = "Withdrew " + amount + " " + account.getCurrency() + ".";
                }
                case EXTEND_MATURITY -> {
                    DepositAccount extended = depositAccountService.extendMaturityDate(id);
                    result = "Maturity date extended to " + extended.getMaturityDate() + ".";
                }
                default -> throw new IllegalArgumentException("Unsupported operation: " + operation);
            }

            redirectAttributes.addFlashAttribute("successMessage", result);
            return "redirect:/accounts";

        } catch (AccountNotFoundException | InactiveAccountException | MaturityNotReachedException
                | IllegalArgumentException ex) {
            // Back to the update form with the error shown next to the account data
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/deposit-accounts/{id}/update";
        }
    }

}
