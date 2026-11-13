package com.dnu.sfm.controller;

import com.dnu.sfm.dto.DepositAccountDto;
import com.dnu.sfm.dto.DepositCloseResultDto;
import com.dnu.sfm.dto.DepositSettlementDto;
import com.dnu.sfm.exception.AccountNotFoundException;
import com.dnu.sfm.exception.CurrencyMismatchException;
import com.dnu.sfm.exception.InactiveAccountException;
import com.dnu.sfm.exception.MaturityNotReachedException;
import com.dnu.sfm.model.Account;
import com.dnu.sfm.model.AccountOperation;
import com.dnu.sfm.model.AccountStatus;
import com.dnu.sfm.model.CashAccount;
import com.dnu.sfm.model.DepositAccount;
import com.dnu.sfm.service.AccountService;
import com.dnu.sfm.service.DepositAccountService;
import com.dnu.sfm.service.impl.DepositInterestCalculatorService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/deposit-accounts")
public class DepositAccountController {

    private final DepositAccountService depositAccountService;
    private final DepositInterestCalculatorService depositInterestCalculatorService;
    private final AccountService accountService;

    // Spring will automatically inject the services here
    public DepositAccountController(DepositAccountService depositAccountService,
                                    DepositInterestCalculatorService depositInterestCalculatorService,
                                    AccountService accountService) {
        this.depositAccountService = depositAccountService;
        this.depositInterestCalculatorService = depositInterestCalculatorService;
        this.accountService = accountService;
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
                "Deposit account \"" + accountDto.name() + "\" was created successfully. "
                        + "Maturity date: " + accountDto.computedMaturityDate() + ".");

        return "redirect:/accounts";
    }

    /**
     * Shows the update form for a single deposit account: deposit funds and,
     * once the maturity date has been reached, close the deposit and withdraw
     * the payout by transferring it to an account of the user's choice.
     */
    @GetMapping("/{id}/update")
    public String showUpdateAccountForm(@PathVariable Long id, Model model) {
        DepositAccount account = depositAccountService.getDepositAccountById(id);
        model.addAttribute("account", account);

        boolean maturityReached = !LocalDate.now().isBefore(account.getMaturityDate());
        model.addAttribute("maturityReached", maturityReached);

        // Payout preview (principal + net interest) and candidate payout
        // accounts for the close & withdraw panel
        if (maturityReached && account.getStatus() == AccountStatus.ACTIVE) {
            model.addAttribute("settlement", depositInterestCalculatorService.calculateSettlement(id));
            model.addAttribute("targetAccounts", activeCashAccountsInCurrency(account.getCurrency()));
        }

        return "update-deposit-account";
    }

    /**
     * Applies the requested change to the deposit account.
     * The extend-maturity operation is hidden from users; withdrawals happen
     * through the close &amp; transfer flow (see {@link #closeAccount}).
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

    /**
     * Closes a matured deposit and withdraws the payout
     * (principal + net interest after tax) by transferring it to the
     * cash account selected by the user. A DEBIT transaction is registered
     * on the deposit and a CREDIT transaction on the target account.
     */
    @PostMapping("/{id}/close")
    public String closeAccount(
            @PathVariable Long id,
            @RequestParam(required = false) Long targetAccountId,
            RedirectAttributes redirectAttributes) {

        try {
            if (targetAccountId == null) {
                throw new IllegalArgumentException("Please select the account that should receive the payout.");
            }

            DepositCloseResultDto closeResult =
                    depositAccountService.closeDepositAccountAndTransfer(id, targetAccountId);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Deposit \"" + closeResult.depositAccountName() + "\" was closed. Payout "
                            + closeResult.payout() + " " + closeResult.currency()
                            + " (principal " + closeResult.principal()
                            + " + net interest " + closeResult.netInterest()
                            + ") transferred to \"" + closeResult.targetAccountName() + "\".");

            return "redirect:/accounts";

        } catch (AccountNotFoundException | InactiveAccountException | MaturityNotReachedException
                | CurrencyMismatchException | IllegalArgumentException ex) {
            // Back to the update form with the error shown next to the account data
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/deposit-accounts/{id}/update";
        }
    }


    @GetMapping("/{id}/settlement")
    public String showSettlement(@PathVariable Long id, Model model) {
        DepositSettlementDto settlement = depositInterestCalculatorService.calculateSettlement(id);
        model.addAttribute("settlement", settlement);
        return "deposit-settlement";
    }

    /**
     * Active cash accounts in the given currency - the allowed payout
     * targets when closing a deposit.
     */
    private List<Account> activeCashAccountsInCurrency(com.dnu.sfm.model.Currency currency) {
        return accountService.getAllAccounts().stream()
                .filter(a -> a instanceof CashAccount
                        && a.getStatus() == AccountStatus.ACTIVE
                        && a.getCurrency() == currency)
                .toList();
    }
}
