package com.dnu.sfm.controller;

import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.StatBureauYearlyInflation;
import com.dnu.sfm.service.StatBureauYearlyInflationService;
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
import java.util.Locale;

@Slf4j
@Controller
@RequestMapping("/inflation/statbureau/annual")
@RequiredArgsConstructor
public class StatBureauYearlyInflationController {

    private final StatBureauYearlyInflationService yearlyInflationService;

    /**
     * Checks database for records corresponding to the selected currency and calendar year.
     *
     * @param currency currency code (UAH, EUR, USD); empty shows the search form only
     * @param year     four-digit year (e.g. 2019); empty shows the search form only
     */
    @GetMapping
    public String viewAnnualInflation(
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String year,
            Model model) {

        if (currency == null || currency.isBlank() || year == null || year.isBlank()) {
            model.addAttribute("searched", false);
            return "statbureau-annual";
        }

        Currency parsedCurrency;
        try {
            parsedCurrency = Currency.valueOf(currency.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            // Bad user input must not bubble up as a whitelabel 500
            log.warn("Rejected invalid currency parameter: '{}'", currency);
            model.addAttribute("searched", false);
            model.addAttribute("errorMessage",
                    "Unknown currency: '" + currency + "'. Please use UAH, EUR or USD.");
            return "statbureau-annual";
        }

        int targetYear;
        try {
            targetYear = Year.parse(year.trim()).getValue();
        } catch (DateTimeParseException ex) {
            log.warn("Rejected invalid year parameter: '{}'", year);
            model.addAttribute("searched", false);
            model.addAttribute("selectedCurrency", parsedCurrency.name());
            model.addAttribute("errorMessage",
                    "Invalid year format: '" + year + "'. Please use a four-digit year (e.g. 2019).");
            return "statbureau-annual";
        }

        List<StatBureauYearlyInflation> records =
                yearlyInflationService.getRecordsForYear(parsedCurrency, targetYear);
        long totalStoredCount = yearlyInflationService.countByCurrency(parsedCurrency);

        boolean dataPresent = !records.isEmpty();
        model.addAttribute("searched", true);
        model.addAttribute("dataPresent", dataPresent);
        model.addAttribute("selectedCurrency", parsedCurrency.name());
        model.addAttribute("selectedYear", targetYear);
        model.addAttribute("records", records);
        model.addAttribute("totalStoredCount", totalStoredCount);

        if (dataPresent) {
            // The stored annual CPI row of the selected year for the highlight banner
            yearlyInflationService.findHeadlineAnnualCpi(parsedCurrency, targetYear)
                    .ifPresent(cpi -> model.addAttribute("headlineCpi", cpi));
        }
        // Newest stored year overall, so stale series (e.g. UAH ends 2019) are visible
        yearlyInflationService.findLatestRecord(parsedCurrency)
                .ifPresent(latest -> model.addAttribute("latestStoredYear", latest.getInflationYear()));

        return "statbureau-annual";
    }

    /**
     * Called when the user clicks "Get data". Fetches the annual inflation rate
     * for the selected currency and year from the StatBureau API, persists it to
     * DB in one transaction, and redirects back to display the saved record
     * without re-calling the API on page reloads.
     */
    @PostMapping("/fetch")
    public String fetchDataFromStatBureau(
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String year,
            RedirectAttributes redirectAttributes) {

        String currencyParam = currency == null ? "" : currency.trim();
        String yearParam = year == null ? "" : year.trim();

        Currency parsedCurrency;
        try {
            parsedCurrency = Currency.valueOf(currencyParam.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Unknown currency: '" + currencyParam + "'. Please use UAH, EUR or USD.");
            return "redirect:/inflation/statbureau/annual";
        }

        int targetYear;
        try {
            targetYear = Year.parse(yearParam).getValue();
        } catch (DateTimeParseException ex) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Invalid year format: '" + yearParam + "'. Please use a four-digit year (e.g. 2019).");
            return "redirect:/inflation/statbureau/annual?currency=" + parsedCurrency.name();
        }

        String redirect = "redirect:/inflation/statbureau/annual?currency=" + parsedCurrency.name()
                + "&year=" + targetYear;

        try {
            // 1. Fetch + convert (no DB connection held during the remote call)
            List<StatBureauYearlyInflation> entities =
                    yearlyInflationService.fetchFromStatBureau(parsedCurrency, targetYear);

            if (entities.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "StatBureau API returned no annual inflation data for " + parsedCurrency + " "
                                + targetYear + ". The year might be outside the available data range.");
                return redirect;
            }

            // 2. Persist atomically (single transaction, upsert on year + currency)
            int stored = yearlyInflationService.upsertYearlyRecords(entities);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Successfully fetched and cached " + stored + " annual inflation record from StatBureau for "
                            + parsedCurrency + " " + targetYear + ".");

        } catch (Exception ex) {
            log.error("Failed to fetch/save StatBureau annual inflation data for {} {}", currencyParam, yearParam, ex);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error fetching data from StatBureau API: " + ex.getMessage());
        }

        return redirect;
    }
}
