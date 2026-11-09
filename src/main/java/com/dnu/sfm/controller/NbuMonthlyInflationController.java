package com.dnu.sfm.controller;

import com.dnu.sfm.model.NbuMonthlyInflation;
import com.dnu.sfm.service.NbuMonthlyInflationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/inflation/nbu/monthly")
@RequiredArgsConstructor
public class NbuMonthlyInflationController {

    private final NbuMonthlyInflationService inflationService;

    /**
     * Checks database for records corresponding to the selected month.
     *
     * @param regionScope empty (default) shows national (Ukraine-wide) rows only;
     *                    "all" additionally shows every stored regional breakdown
     */
    @GetMapping
    public String viewMonthlyInflation(
            @RequestParam(required = false) String date,
            @RequestParam(name = "region", required = false, defaultValue = "") String regionScope,
            Model model) {

        model.addAttribute("region", regionScope);

        if (date == null || date.isBlank()) {
            model.addAttribute("searched", false);
            return "nbu-monthly-inflation";
        }

        LocalDate normalizedDate;
        try {
            normalizedDate = parseMonthToFirstDay(date);
        } catch (DateTimeParseException ex) {
            // Bad user input must not bubble up as a whitelabel 500
            log.warn("Rejected invalid month parameter: '{}'", date);
            model.addAttribute("searched", false);
            model.addAttribute("errorMessage",
                    "Invalid date format: '" + date + "'. Please use yyyy-MM (e.g. 2025-01).");
            return "nbu-monthly-inflation";
        }

        String yearMonthStr = normalizedDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        List<NbuMonthlyInflation> records = inflationService.getRecordsForView(normalizedDate, regionScope);
        long totalStoredCount = inflationService.countByRecordDate(normalizedDate);

        boolean dataPresent = !records.isEmpty();
        model.addAttribute("searched", true);
        model.addAttribute("dataPresent", dataPresent);
        model.addAttribute("selectedMonth", yearMonthStr);
        model.addAttribute("records", records);
        model.addAttribute("totalStoredCount", totalStoredCount);

        if (dataPresent) {
            // Find headline national MoM CPI for the highlight banner
            inflationService.findHeadlineCpi(normalizedDate)
                    .ifPresent(cpi -> model.addAttribute("headlineCpi", cpi));
        }

        return "nbu-monthly-inflation";
    }

    /**
     * Called when the user clicks "Get data". Fetches from the NBU API, persists
     * to DB in one transaction, and redirects back to display the saved records
     * without re-calling the API on page reloads.
     */
    @PostMapping("/fetch")
    public String fetchDataFromNbu(
            @RequestParam String date,
            RedirectAttributes redirectAttributes) {

        try {
            LocalDate targetDate = parseMonthToFirstDay(date);

            // 1. Fetch + convert (no DB connection held during the remote call)
            List<NbuMonthlyInflation> entities = inflationService.fetchFromNbu(targetDate);

            if (entities.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "NBU API returned 0 records for " + date + ". The data might not be published yet.");
                return "redirect:/inflation/nbu/monthly?date=" + date;
            }

            // 2. Persist atomically (single transaction, bulk upsert)
            int stored = inflationService.upsertMonthlyRecords(entities);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Successfully fetched and cached " + stored + " records from NBU for " + date);

        } catch (DateTimeParseException ex) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Invalid date format: '" + date + "'. Please use yyyy-MM (e.g. 2025-01).");
        } catch (Exception ex) {
            log.error("Failed to fetch/save NBU inflation data for {}", date, ex);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error fetching data from NBU API: " + ex.getMessage());
        }

        return "redirect:/inflation/nbu/monthly?date=" + date;
    }

    private LocalDate parseMonthToFirstDay(String dateStr) {
        if (dateStr.length() == 7) { // yyyy-MM
            return YearMonth.parse(dateStr).atDay(1);
        }
        return LocalDate.parse(dateStr).withDayOfMonth(1);
    }
}
