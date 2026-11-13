package com.dnu.sfm.controller;

import com.dnu.sfm.dto.TransferFormDto;
import com.dnu.sfm.dto.TransferResultDto;
import com.dnu.sfm.exception.AccountNotFoundException;
import com.dnu.sfm.exception.CurrencyMismatchException;
import com.dnu.sfm.exception.InactiveAccountException;
import com.dnu.sfm.model.Account;
import com.dnu.sfm.model.AccountStatus;
import com.dnu.sfm.model.CashAccount;
import com.dnu.sfm.service.AccountService;
import com.dnu.sfm.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Handles the "Transfer Funds" page: a user can transfer money between cash
 * accounts and from a cash account to a deposit account. Each transfer
 * registers a transaction in both accounts - debit on the source and credit
 * on the target.
 */
@Controller
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transferService;
    private final AccountService accountService;

    public TransferController(TransferService transferService, AccountService accountService) {
        this.transferService = transferService;
        this.accountService = accountService;
    }

    /**
     * Shows the transfer form: source accounts are ACTIVE cash accounts,
     * target accounts are all ACTIVE accounts (cash and deposit).
     */
    @GetMapping("/new")
    public String showTransferForm(Model model) {
        if (!model.containsAttribute("transfer")) {
            model.addAttribute("transfer", new TransferFormDto());
        }
        model.addAttribute("fromAccounts", activeCashAccounts());
        model.addAttribute("toAccounts", activeAccounts());
        return "transfer-form";
    }

    /**
     * Cross-account transfer history: one row per completed movement
     * (user transfers and deposit-close payouts), newest first.
     */
    @GetMapping("/history")
    public String transferHistory(Model model) {
        model.addAttribute("entries", transferService.getTransferHistory());
        return "transfer-history";
    }

    /**
     * Executes the transfer and records the DEBIT/CREDIT transactions
     * in both accounts. On success returns to the accounts list with a
     * confirmation message; on failure re-renders the form with the error.
     */
    @PostMapping("/new")
    public String transfer(
            @Valid @ModelAttribute("transfer") TransferFormDto transferForm,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            // Re-render the form with the submitted values and field errors
            model.addAttribute("fromAccounts", activeCashAccounts());
            model.addAttribute("toAccounts", activeAccounts());
            return "transfer-form";
        }

        try {
            TransferResultDto result =
                    transferService.transfer(transferForm.fromAccountId(), transferForm.toAccountId(), transferForm.amount());

            redirectAttributes.addFlashAttribute("successMessage",
                    "Transferred " + result.amount() + " " + result.currency()
                            + " from \"" + result.fromAccountName() + "\" to \"" + result.toAccountName()
                            + "\". Transactions recorded: DEBIT on \"" + result.fromAccountName()
                            + "\", CREDIT on \"" + result.toAccountName() + "\"."
                            + " Reference: " + result.transferRef());

            return "redirect:/accounts";

        } catch (AccountNotFoundException | InactiveAccountException | CurrencyMismatchException
                | IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("fromAccounts", activeCashAccounts());
            model.addAttribute("toAccounts", activeAccounts());
            return "transfer-form";
        }
    }

    private List<Account> activeCashAccounts() {
        return accountService.getAllAccounts().stream()
                .filter(a -> a instanceof CashAccount && a.getStatus() == AccountStatus.ACTIVE)
                .toList();
    }

    private List<Account> activeAccounts() {
        return accountService.getAllAccounts().stream()
                .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                .toList();
    }
}
