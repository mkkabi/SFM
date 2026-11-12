package com.dnu.sfm.client;

import com.dnu.sfm.config.StatBureauApiProperties;
import com.dnu.sfm.dto.MonthlyInflationStatBureauRecordDto;
import com.dnu.sfm.model.Currency;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class StatBureauInflationClient {

    private static final Pattern ASPNET_DATE_PATTERN = Pattern.compile("^/Date\\((-?\\d+)(?:[+-]\\d+)?\\)/$");
    private static final MathContext MC = MathContext.DECIMAL64;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    // don't use for anything other than USD - OLD DATA!!!
    public StatBureauInflationClient(RestClient.Builder builder, ObjectMapper objectMapper,
                                     StatBureauApiProperties properties) {
        this.objectMapper = (objectMapper != null)
                ? objectMapper
                : new ObjectMapper().registerModule(new JavaTimeModule());

        this.restClient = builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 SFM/1.0")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE + ", text/plain, */*")
                .build();
    }

    /**
     * Fetches monthly historical inflation rates for a given currency/country slug.
     * Safely parses ASP.NET "/Date(...)/" formats and ISO date strings.
     */
    public List<MonthlyInflationStatBureauRecordDto> getHistoricalInflation(Currency currency) {
        if (currency == null || currency.getCountrySlug() == null) {
            log.warn("Cannot fetch inflation: currency or country slug is null");
            return Collections.emptyList();
        }

        try {
            String rawJson = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/get-data-json")
                            .queryParam("country", currency.getCountrySlug())
                            .build())
                    .retrieve()
                    .body(String.class);

            if (rawJson == null || rawJson.isBlank()) {
                log.warn("Empty response received from StatBureau for country: {}", currency.getCountrySlug());
                return Collections.emptyList();
            }

            return parseMonthlyRecords(rawJson);

        } catch (Exception ex) {
            log.error("Failed to fetch historical monthly inflation for {}: {}", currency, ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Calculates cumulative inflation rate between two dates via StatBureau.
     * Sanitizes strings containing quotes, commas, and '%' symbols before BigDecimal conversion.
     */
    public BigDecimal calculateInflationRate(Currency currency, String startDate, String endDate) {
        if (currency == null) {
            return null;
        }

        try {
            String rawResponse = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/calculate-inflation-rate-json")
                            .queryParam("country", currency.getCountrySlug())
                            .queryParam("start", startDate)
                            .queryParam("end", endDate)
                            .build())
                    .retrieve()
                    .body(String.class);

            if (rawResponse != null && !rawResponse.isBlank()) {
                return parseRateString(rawResponse);
            }
        } catch (Exception ex) {
            log.warn("StatBureau calculate-inflation-rate-json failed for {} ({} to {}): {}",
                    currency, startDate, endDate, ex.getMessage());
        }

        return null;
    }

    /**
     * Fetches the annual inflation rate for a full calendar year.
     * Tries the remote calculate endpoint first; if unavailable, compounds monthly data as fallback.
     */
    public BigDecimal fetchAnnualRate(Currency currency, int year) {
        if (currency == null) {
            return null;
        }

        // 1. Primary approach: StatBureau calculator endpoint (Jan 1 to Dec 1)
        String startDate = year + "/1/1";
        String endDate = year + "/12/1";
        BigDecimal calculated = calculateInflationRate(currency, startDate, endDate);

        if (calculated != null) {
            return calculated;
        }

        // 2. Mathematical fallback: Compound (1 + r1)(1 + r2)...(1 + r12) - 1 from monthly data
        log.info("StatBureau calculate endpoint returned null for {} {}. Falling back to monthly compounding.", currency, year);
        return computeAnnualRateFromMonthly(currency, year);
    }

    private List<MonthlyInflationStatBureauRecordDto> parseMonthlyRecords(String json) {
        List<MonthlyInflationStatBureauRecordDto> records = new ArrayList<>();
        try {
            JsonNode rootNode = objectMapper.readTree(json);
            if (!rootNode.isArray()) {
                log.warn("Expected JSON array from StatBureau but received: {}", rootNode.getNodeType());
                return records;
            }

            for (JsonNode node : rootNode) {
                LocalDate month = extractDate(node.get("Month"));
                if (month == null) {
                    continue;
                }

                BigDecimal rate = extractRate(node.get("InflationRate"));
                String formatted = extractFormattedRate(node.get("InflationRateFormatted"), rate);

                records.add(new MonthlyInflationStatBureauRecordDto(month, rate, formatted));
            }
        } catch (Exception ex) {
            log.error("Error parsing StatBureau monthly JSON response: {}", ex.getMessage());
        }
        return records;
    }

    private LocalDate extractDate(JsonNode monthNode) {
        if (monthNode == null || monthNode.isNull()) {
            return null;
        }

        if (monthNode.isNumber()) {
            return Instant.ofEpochMilli(monthNode.asLong())
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                    .withDayOfMonth(1);
        }

        String rawText = monthNode.asText().trim();
        Matcher matcher = ASPNET_DATE_PATTERN.matcher(rawText);
        if (matcher.matches()) {
            long epochMillis = Long.parseLong(matcher.group(1));
            return Instant.ofEpochMilli(epochMillis)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                    .withDayOfMonth(1);
        }

        // Handle standard ISO dates (e.g., "2024-01-01")
        if (rawText.length() >= 10 && rawText.charAt(4) == '-' && rawText.charAt(7) == '-') {
            try {
                return LocalDate.parse(rawText.substring(0, 10)).withDayOfMonth(1);
            } catch (Exception ignored) {
            }
        }

        log.warn("Unrecognized date format from StatBureau: {}", rawText);
        return null;
    }

    private BigDecimal extractRate(JsonNode rateNode) {
        if (rateNode == null || rateNode.isNull()) {
            return BigDecimal.ZERO;
        }
        if (rateNode.isNumber()) {
            return BigDecimal.valueOf(rateNode.asDouble()).setScale(4, RoundingMode.HALF_EVEN);
        }
        return parseRateString(rateNode.asText());
    }

    private String extractFormattedRate(JsonNode formattedNode, BigDecimal rate) {
        if (formattedNode != null && !formattedNode.isNull() && !formattedNode.asText().isBlank()) {
            return formattedNode.asText().trim();
        }
        return (rate != null) ? rate.setScale(2, RoundingMode.HALF_EVEN).toPlainString() + "%" : "0.00%";
    }

    private BigDecimal parseRateString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String cleaned = raw.replace("\"", "")
                    .replace("'", "")
                    .replace("%", "")
                    .trim()
                    .replace(',', '.');
            return new BigDecimal(cleaned).setScale(4, RoundingMode.HALF_EVEN);
        } catch (Exception ex) {
            log.warn("Failed to parse numeric inflation value from string '{}': {}", raw, ex.getMessage());
            return null;
        }
    }

    private BigDecimal computeAnnualRateFromMonthly(Currency currency, int year) {
        List<MonthlyInflationStatBureauRecordDto> history = getHistoricalInflation(currency);
        if (history.isEmpty()) {
            return null;
        }

        List<MonthlyInflationStatBureauRecordDto> yearRecords = history.stream()
                .filter(r -> r.month() != null && r.month().getYear() == year)
                .toList();

        if (yearRecords.isEmpty()) {
            log.warn("No monthly records found to compound annual inflation for {} in year {}", currency, year);
            return null;
        }

        BigDecimal multiplier = BigDecimal.ONE;
        for (MonthlyInflationStatBureauRecordDto record : yearRecords) {
            BigDecimal monthlyFraction = record.ratePercent().divide(BigDecimal.valueOf(100), MC);
            multiplier = multiplier.multiply(BigDecimal.ONE.add(monthlyFraction), MC);
        }

        return multiplier.subtract(BigDecimal.ONE, MC)
                .multiply(BigDecimal.valueOf(100), MC)
                .setScale(4, RoundingMode.HALF_EVEN);
    }
}