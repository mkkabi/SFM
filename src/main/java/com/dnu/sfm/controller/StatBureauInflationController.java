package com.dnu.sfm.controller;

import com.dnu.sfm.model.Currency;
import com.dnu.sfm.model.StatBureauMonthlyInflation;
import com.dnu.sfm.service.StatBureauInflationService;
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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

@Slf4j
@Controller
@RequestMapping("/inflation/statbureau/monthly")
@RequiredArgsConstructor
public class StatBureauInflationController {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final StatBureauInflationService statBureauInflationService;

    /**
     * Checks database for records of the selected currency and calendar year.
     *
     * @param currency currency code (UAH, EUR, USD); empty shows the search form only
     * @param year     four-digit year (e.g. 2019); empty shows the search form only
     */
    @GetMapping
    public String viewMonthlyInflation(
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String year,
            Model model) {

        if (currency == null || currency.isBlank() || year == null || year.isBlank()) {
            model.addAttribute("searched", false);
            return "statbureau-monthly";
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
            return "statbureau-monthly";
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
            return "statbureau-monthly";
        }

        List<StatBureauMonthlyInflation> records =
                statBureauInflationService.getRecordsForYear(parsedCurrency, targetYear);
        long totalStoredCount = statBureauInflationService.countByCurrency(parsedCurrency);

        boolean dataPresent = !records.isEmpty();
        model.addAttribute("searched", true);
        model.addAttribute("dataPresent", dataPresent);
        model.addAttribute("selectedCurrency", parsedCurrency.name());
        model.addAttribute("selectedYear", targetYear);
        model.addAttribute("records", records);
        model.addAttribute("totalStoredCount", totalStoredCount);

        if (dataPresent) {
            // Newest stored month of the selected year for the highlight banner
            statBureauInflationService.findHeadlineMonthlyCpi(parsedCurrency, targetYear)
                    .ifPresent(cpi -> model.addAttribute("headlineCpi", cpi));
        }
        // Newest stored month overall, so stale series (e.g. UAH ends 2019-12) are visible
        statBureauInflationService.findLatestRecord(parsedCurrency)
                .ifPresent(latest -> model.addAttribute("latestStoredMonth",
                        latest.getRecordDate().format(MONTH_FORMAT)));

        return "statbureau-monthly";
    }

    /**
     * Called when the user clicks "Get data". Fetches the full available monthly
     * history of the currency from the StatBureau API, persists it to DB in one
     * transaction, and redirects back to display the requested year without
     * re-calling the API on page reloads.
     */
    @PostMapping("/fetch")
    public String fetchDataFromStatBureau(
            @RequestParam String currency,
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
            return "redirect:/inflation/statbureau/monthly";
        }

        String redirect = "redirect:/inflation/statbureau/monthly?currency=" + parsedCurrency.name()
                + (yearParam.isEmpty() ? "" : "&year=" + yearParam);

        try {
            // 1. Fetch + convert (no DB connection held during the remote call)
            List<StatBureauMonthlyInflation> entities = statBureauInflationService.fetchFromStatBureau(parsedCurrency);

            if (entities.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "StatBureau API returned 0 records for " + parsedCurrency + ".");
                return redirect;
            }

            // 2. Persist atomically (single transaction, bulk upsert)
            int stored = statBureauInflationService.upsertMonthlyRecords(entities);

            // 3. Tell the user whether the fetched history actually covers the requested year
            Integer targetYear = parseYearOrNull(yearParam);
            if (targetYear != null) {
                long yearCount = statBureauInflationService.countByYear(parsedCurrency, targetYear);
                if (yearCount > 0) {
                    redirectAttributes.addFlashAttribute("successMessage",
                            "Successfully fetched and cached " + stored + " history records from StatBureau for "
                                    + parsedCurrency + "; " + yearCount + " of them cover " + targetYear + ".");
                } else {
                    String newestMonth = statBureauInflationService.findLatestRecord(parsedCurrency)
                            .map(latest -> latest.getRecordDate().format(MONTH_FORMAT))
                            .orElse("n/a");
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Cached " + stored + " StatBureau history records for " + parsedCurrency
                                    + ", but the stored series has no data for " + targetYear
                                    + " (newest stored month: " + newestMonth + ").");
                }
            } else {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Successfully fetched and cached " + stored + " history records from StatBureau for "
                                + parsedCurrency + ".");
            }

        } catch (Exception ex) {
            log.error("Failed to fetch/save StatBureau monthly inflation data for {}", currencyParam, ex);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error fetching data from StatBureau API: " + ex.getMessage());
        }

        return redirect;
    }

    private static Integer parseYearOrNull(String yearParam) {
        if (yearParam.isEmpty()) {
            return null;
        }
        try {
            return Year.parse(yearParam).getValue();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
