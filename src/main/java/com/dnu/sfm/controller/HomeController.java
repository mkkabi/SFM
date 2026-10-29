package com.dnu.sfm.controller;


import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.logging.Logger;

@Controller
public class HomeController {
//    private final UserService userService;
//    private final Logger logger = new AppLogger(this.getClass().getSimpleName());
//    public HomeController(UserService userService) {
//        this.userService = userService;
//    }

    @GetMapping({"/", "home"})
    public String home(Model model) {
//        model.addAttribute("users", userService.getAll());
        return "home";
    }

    @GetMapping({"/setup-data"})
    public String setup(Model model) {
        model.addAttribute("warning", "nothing was set");
        return "redirect:/";
    }


    @GetMapping({"/add-account"})
    public String addAccount(Model model) {
//        model.addAttribute("users", userService.getAll());
        return "add-account_backup";
    }


}
