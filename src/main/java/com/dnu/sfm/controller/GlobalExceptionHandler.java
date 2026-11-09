package com.dnu.sfm.controller;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeParseException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    public String handleIllegalStateException(IllegalStateException ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/accounts";
    }

    /**
     * A request parameter could not be converted to its expected type
     * (e.g. a malformed AccountOperation enum value in the update forms).
     * Without this handler Spring MVC would render a raw whitelabel 500 page.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public String handleTypeMismatch(MethodArgumentTypeMismatchException ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage",
                "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'.");
        return "redirect:/accounts";
    }

    /**
     * Safety net for unparsed dates reaching the web layer; the NBU inflation
     * controller already catches this locally for friendlier in-page messaging.
     */
    @ExceptionHandler(DateTimeParseException.class)
    public String handleDateParse(DateTimeParseException ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage",
                "Invalid date format. Please use yyyy-MM (e.g. 2025-01).");
        return "redirect:/inflation/nbu/monthly";
    }

    /**
     * Concurrent modification detected via JPA optimistic locking (@Version on
     * Account): another operation updated the same row first. Shown as a normal
     * flash message instead of a 500.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public String handleConcurrentModification(OptimisticLockingFailureException ex,
                                               RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage",
                "The record was modified by another operation. Please review the current state and try again.");
        return "redirect:/accounts";
    }
}
