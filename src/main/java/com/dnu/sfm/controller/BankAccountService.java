package com.dnu.sfm.controller;



import com.dnu.sfm.dto.AccountDto;
import com.dnu.sfm.service.AccountService;
import com.dnu.sfm.service.DepositAccountService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/accounts")
public class DepositAccountController {
    @Autowired
    private final DepositAccountService depositAccountService;

    // Spring will automatically inject AccountService here
    public DepositAccountController(DepositAccountService depositAccountService) {
        this.depositAccountService = depositAccountService;
    }


    @GetMapping("/new")
    public String showCreateAccountForm(Model model) {
        model.addAttribute("account", new AccountDto("", "", "", "USD", java.math.BigDecimal.ZERO, "", ""));
        return "add-account";
    }

    @PostMapping("/new")
    public String createAccount(
            @Valid @ModelAttribute("account") AccountDto accountDto,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "add-account"; // or "accounts/add-account"
        }

        depositAccountService.createDepositAccount(accountDto);

        // Flash attribute is automatically removed from session after the redirect renders
        redirectAttributes.addFlashAttribute("successMessage",
                "Account \"" + accountDto.name() + "\" was created successfully.");

        return "redirect:/accounts";
    }
}