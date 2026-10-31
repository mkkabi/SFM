package com.dnu.sfm.controller;

import com.dnu.sfm.dto.TransactionDto;
import com.dnu.sfm.exception.AccountNotFoundException;
import com.dnu.sfm.model.Account;
import com.dnu.sfm.service.AccountService;
import com.dnu.sfm.service.TransactionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Displays the transaction history of a single account.
 */
@Controller
@RequestMapping("/accounts/{accountId}/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final AccountService accountService;

    // Spring will automatically inject the services here
    public TransactionController(TransactionService transactionService, AccountService accountService) {
        this.transactionService = transactionService;
        this.accountService = accountService;
    }

    /**
     * Shows all transactions made with the account, newest first.
     */
    @GetMapping
    public String showTransactionsByAccount(
            @PathVariable Long accountId,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            Account account = accountService.getAccountById(accountId);
            List<TransactionDto> transactions = transactionService.getTransactionsByAccount(accountId);

            model.addAttribute("account", account);
            model.addAttribute("transactions", transactions);

            return "transactions";

        } catch (AccountNotFoundException ex) {
            // Unknown account id - back to the accounts list with an error message
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/accounts";
        }
    }

}
