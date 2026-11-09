package com.dnu.sfm.controller;

import com.dnu.sfm.model.NbuYearlyInflation;
import com.dnu.sfm.service.NbuYearlyInflationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Year;
import java.time.format.DateTimeParseException;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/inflation/nbu/yearly")
@RequiredArgsConstructor
public class NbuYearlyInflationController {

    private final NbuYearlyInflationService yearlyInflationService;

    /**
     * Checks database for records corresponding to the selected calendar year.
     *
     * @param year four-digit year (e.g. 2024); empty shows the search form only
     */
    @GetMapping
    public String viewYearlyInflation(
            @RequestParam(required = false) String year,
            Model model) {

        if (year == null || year.isBlank()) {
            model.addAttribute("searched", false);
            return "nbu-yearly-inflation";
        }

        Year parsedYear;
        try {
            parsedYear = Year.parse(year.trim());
        } catch (DateTimeParseException ex) {
            // Bad user input must not bubble up as a whitelabel 500
            log.warn("Rejected invalid year parameter: '{}'", year);
            model.addAttribute("searched", false);
            model.addAttribute("errorMessage",
                    "Invalid year format: '" + year + "'. Please use a four-digit year (e.g. 2024).");
            return "nbu-yearly-inflation";
        }

        int targetYear = parsedYear.getValue();

        List<NbuYearlyInflation> records = yearlyInflationService.getRecordsForYear(targetYear);
        long totalStoredCount = yearlyInflationService.countByYear(targetYear);

        boolean dataPresent = !records.isEmpty();
        model.addAttribute("searched", true);
        model.addAttribute("dataPresent", dataPresent);
        model.addAttribute("selectedYear", targetYear);
        model.addAttribute("records", records);
        model.addAttribute("totalStoredCount", totalStoredCount);

        if (dataPresent) {
            // Headline national annual CPI (year-over-year) for the highlight banner
            yearlyInflationService.findHeadlineAnnualCpi(targetYear)
                    .ifPresent(cpi -> model.addAttribute("headlineCpi", cpi));
        }

        return "nbu-yearly-inflation";
    }

    /**
     * Called when the user clicks "Get data". Fetches from the NBU API, persists
     * to DB in one transaction, and redirects back to display the saved records
     * without re-calling the API on page reloads.
     */
    @PostMapping("/fetch")
    public String fetchDataFromNbu(
            @RequestParam String year,
            RedirectAttributes redirectAttributes) {

        try {
            int targetYear = Year.parse(year.trim()).getValue();

            // 1. Fetch + convert (no DB connection held during the remote call)
            List<NbuYearlyInflation> entities = yearlyInflationService.fetchFromNbu(targetYear);

            if (entities.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "NBU API returned 0 records for " + targetYear + ". The data might not be published yet.");
                return "redirect:/inflation/nbu/yearly?year=" + targetYear;
            }

            // 2. Persist atomically (single transaction, bulk upsert)
            int stored = yearlyInflationService.upsertYearlyRecords(entities);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Successfully fetched and cached " + stored + " records from NBU for " + targetYear);

        } catch (DateTimeParseException ex) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Invalid year format: '" + year + "'. Please use a four-digit year (e.g. 2024).");
        } catch (Exception ex) {
            log.error("Failed to fetch/save NBU yearly inflation data for {}", year, ex);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error fetching data from NBU API: " + ex.getMessage());
        }

        return "redirect:/inflation/nbu/yearly?year=" + year;
    }
}
