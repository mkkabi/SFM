package com.dnu.sfm.controller;

import com.dnu.sfm.model.WorldBankInflation;
import com.dnu.sfm.service.WorldBankInflationService;
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
@RequestMapping("/inflation/worldbank/annual")
@RequiredArgsConstructor
public class WorldBankInflationController {

    private final WorldBankInflationService worldBankInflationService;

    /**
     * Checks database for records corresponding to the selected calendar year.
     *
     * @param year four-digit year (e.g. 2024); empty shows the search form only
     */
    @GetMapping
    public String viewAnnualInflation(
            @RequestParam(required = false) String year,
            Model model) {

        if (year == null || year.isBlank()) {
            model.addAttribute("searched", false);
            return "worldbank-annual";
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
            return "worldbank-annual";
        }

        int targetYear = parsedYear.getValue();

        List<WorldBankInflation> records = worldBankInflationService.getRecordsForYear(targetYear);
        long totalStoredCount = worldBankInflationService.countByYear(targetYear);

        boolean dataPresent = !records.isEmpty();
        model.addAttribute("searched", true);
        model.addAttribute("dataPresent", dataPresent);
        model.addAttribute("selectedYear", targetYear);
        model.addAttribute("records", records);
        model.addAttribute("totalStoredCount", totalStoredCount);

        if (dataPresent) {
            // Headline Ukraine annual CPI for the highlight banner
            worldBankInflationService.findHeadlineAnnualCpi(targetYear)
                    .ifPresent(cpi -> model.addAttribute("headlineCpi", cpi));
        }

        return "worldbank-annual";
    }

    /**
     * Called when the user clicks "Get data". Fetches from the World Bank API
     * (one request per tracked currency), persists to DB in one transaction,
     * and redirects back to display the saved records without re-calling the
     * API on page reloads.
     */
    @PostMapping("/fetch")
    public String fetchDataFromWorldBank(
            @RequestParam String year,
            RedirectAttributes redirectAttributes) {

        try {
            int targetYear = Year.parse(year.trim()).getValue();

            // 1. Fetch + convert (no DB connection held during the remote call)
            List<WorldBankInflation> entities = worldBankInflationService.fetchFromWorldBank(targetYear);

            if (entities.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "World Bank API returned 0 records for " + targetYear + ". The data might not be published yet.");
                return "redirect:/inflation/worldbank/annual?year=" + targetYear;
            }

            // 2. Persist atomically (single transaction, bulk upsert)
            int stored = worldBankInflationService.upsertAnnualRecords(entities);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Successfully fetched and cached " + stored + " records from the World Bank for " + targetYear);

        } catch (DateTimeParseException ex) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Invalid year format: '" + year + "'. Please use a four-digit year (e.g. 2024).");
        } catch (Exception ex) {
            log.error("Failed to fetch/save World Bank annual inflation data for {}", year, ex);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error fetching data from World Bank API: " + ex.getMessage());
        }

        return "redirect:/inflation/worldbank/annual?year=" + year;
    }
}
