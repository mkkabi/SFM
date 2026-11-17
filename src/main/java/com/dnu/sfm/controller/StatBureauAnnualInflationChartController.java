package com.dnu.sfm.controller;

import com.dnu.sfm.dto.AnnualInflationPointDto;
import com.dnu.sfm.dto.AnnualInflationScatterSeriesDto;
import com.dnu.sfm.service.StatBureauInflationChartService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Serves the StatBureau annual inflation scatter plot page for the US economy
 * (USD only). The chart range is chosen with {@code ?years=2|5|10}; the payload
 * itself is assembled by {@link StatBureauInflationChartService}, which reads
 * stored years from the database and transparently fetches + stores any missing
 * year from the StatBureau API.
 */
@Slf4j
@Controller
@RequestMapping("/inflation/statbureau")
@RequiredArgsConstructor
public class StatBureauAnnualInflationChartController {

    /** Chart ranges the user can pick from; anything else is rejected. */
    static final List<Integer> ALLOWED_RANGES = List.of(2, 5, 10);

    /** Range used when the page is opened without an explicit choice. */
    static final int DEFAULT_RANGE = 5;

    private final StatBureauInflationChartService chartService;
    private final ObjectMapper objectMapper;

    @GetMapping("/annual-chart")
    public String annualInflationChart(
            @RequestParam(required = false) Integer years,
            Model model) {

        model.addAttribute("allowedRanges", ALLOWED_RANGES);

        if (years != null && !ALLOWED_RANGES.contains(years)) {
            // Bad input must not bubble up as a whitelabel 500
            log.warn("Rejected invalid chart range parameter: {}", years);
            model.addAttribute("errorMessage",
                    "Invalid range '" + years + "'. Please choose 2, 5 or 10 years.");
            return "statbureau-annual-inflation-chart";
        }

        int selectedYears = years != null ? years : DEFAULT_RANGE;
        AnnualInflationScatterSeriesDto series =
                chartService.getAnnualInflationScatterData(selectedYears);

        model.addAttribute("selectedYears", selectedYears);
        model.addAttribute("series", series);

        if (!series.points().isEmpty()) {
            model.addAttribute("stats", computeStats(series.points()));
        }

        try {
            // Pre-serialized JSON for the Chart.js bootstrap (see the template script)
            model.addAttribute("pointsJson", objectMapper.writeValueAsString(series.points()));
            model.addAttribute("chartMetaJson", objectMapper.writeValueAsString(
                    Map.of("fromYear", series.fromYear(), "toYear", series.toYear())));
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize chart payload", ex);
            model.addAttribute("pointsJson", "[]");
            model.addAttribute("chartMetaJson", "{}");
        }

        return "statbureau-annual-inflation-chart";
    }

    /** Summary strip values: average / highest / lowest / most recent year. */
    private ChartStats computeStats(List<AnnualInflationPointDto> points) {
        double average = points.stream()
                .mapToDouble(AnnualInflationPointDto::ratePercent)
                .average()
                .orElse(Double.NaN);
        AnnualInflationPointDto highest = points.stream()
                .max(Comparator.comparingDouble(AnnualInflationPointDto::ratePercent))
                .orElseThrow();
        AnnualInflationPointDto lowest = points.stream()
                .min(Comparator.comparingDouble(AnnualInflationPointDto::ratePercent))
                .orElseThrow();
        AnnualInflationPointDto latest = points.stream()
                .max(Comparator.comparingInt(AnnualInflationPointDto::year))
                .orElseThrow();
        return new ChartStats(average, highest, lowest, latest);
    }

    /** Presentation-only aggregate values shown under the chart. */
    record ChartStats(
            double average,
            AnnualInflationPointDto highest,
            AnnualInflationPointDto lowest,
            AnnualInflationPointDto latest) {
    }
}
