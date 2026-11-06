package com.dnu.sfm.controller;

import com.dnu.sfm.client.NbuInflationClient;
import com.dnu.sfm.dto.NbuMonthlyInflationRecordDto;
import com.dnu.sfm.model.NbuMonthlyInflation;
import com.dnu.sfm.repository.NbuMonthlyInflationRepository;
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
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/inflation/nbu/monthly")
@RequiredArgsConstructor
public class NbuMonthlyInflationController {

    private final NbuMonthlyInflationRepository inflationRepository;
    private final NbuInflationClient nbuInflationClient;

    /**
     * Checks database for records corresponding to the selected month.
     */
    @GetMapping
    public String viewMonthlyInflation(
            @RequestParam(required = false) String date,
            Model model) {

        if (date == null || date.isBlank()) {
            model.addAttribute("searched", false);
            return "nbu-monthly-inflation";
        }

        LocalDate normalizedDate = parseMonthToFirstDay(date);
        String yearMonthStr = normalizedDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        List<NbuMonthlyInflation> records = inflationRepository.findByRecordDateOrderByCommodityGroupAsc(normalizedDate);

        boolean dataPresent = !records.isEmpty();
        model.addAttribute("searched", true);
        model.addAttribute("dataPresent", dataPresent);
        model.addAttribute("selectedMonth", yearMonthStr);
        model.addAttribute("records", records);

        if (dataPresent) {
            // Find headline MoM CPI for highlight banner
            records.stream()
                    .filter(r -> "PCPM_".equalsIgnoreCase(r.getIndexType()) && "Total".equalsIgnoreCase(r.getCommodityGroup()))
                    .findFirst()
                    .ifPresent(cpi -> model.addAttribute("headlineCpi", cpi));
        }

        return "nbu-monthly-inflation";
    }

    /**
     * Called when the user clicks "Get data". Fetches from NBU API, persists to DB,
     * and redirects back to display the saved records without re-calling the API on page reloads.
     */
    @PostMapping("/fetch")
    public String fetchDataFromNbu(
            @RequestParam String date,
            RedirectAttributes redirectAttributes) {

        try {
            LocalDate targetDate = parseMonthToFirstDay(date);
            List<NbuMonthlyInflationRecordDto> dtos = nbuInflationClient.getUkraineMonthlyInflation(targetDate);

            if (dtos.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "NBU API returned 0 records for " + date + ". The data might not be published yet.");
                return "redirect:/inflation/nbu/monthly?date=" + date;
            }

            // Persist or update entities
            List<NbuMonthlyInflation> entities = dtos.stream()
                    .map(NbuMonthlyInflation::fromDto)
                    .toList();

            for (NbuMonthlyInflation entity : entities) {
                inflationRepository.findByRecordDateAndIndexTypeAndCommodityGroup(
                        entity.getRecordDate(), entity.getIndexType(), entity.getCommodityGroup()
                ).ifPresentOrElse(
                        existing -> {
                            existing.setRatePercent(entity.getRatePercent());
                            existing.setIdApi(entity.getIdApi());
                            inflationRepository.save(existing);
                        },
                        () -> inflationRepository.save(entity)
                );
            }

            redirectAttributes.addFlashAttribute("successMessage",
                    "Successfully fetched and cached " + entities.size() + " records from NBU for " + date);

        } catch (Exception ex) {
            log.error("Failed to fetch/save NBU inflation data: {}", ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error fetching data from NBU API: " + ex.getMessage());
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