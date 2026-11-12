package com.dnu.sfm.client;

import com.dnu.sfm.config.StatBureauApiProperties;
import com.dnu.sfm.dto.MonthlyInflationStatBureauRecordDto;
import com.dnu.sfm.dto.YearlyInflationStatBureauRecordDto;
import com.dnu.sfm.model.Currency;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * Client for the StatBureau.org <em>annual</em> inflation figures of one economy.
 * <p>
 * StatBureau originally offered a dedicated yearly history endpoint
 * ({@code get-yearly-inflation-data-json}), but that resource has been retired
 * and now answers with an ASP.NET "resource cannot be found" page. Annual rates
 * are therefore obtained in two steps:
 * <ol>
 *     <li><strong>Primary:</strong> the {@code calculate-inflation-rate-json}
 *     endpoint, asked for January 1 to December 1 of the requested year -
 *     StatBureau itself accumulates the calendar year on that window, and the
 *     result matches the official annual (Dec-to-Dec) CPI growth closely
 *     (e.g. UAH 2019 &rarr; 4.05%, USD 2024 &rarr; 2.89%). For years outside
 *     the cached series the endpoint answers with the literal string
 *     {@code "0"}, which is treated as "no data" (a real calendar year of CPI
 *     inflation is never exactly 0%).</li>
 *     <li><strong>Fallback:</strong> if the calculator endpoint is unreachable
 *     or returns nothing for the year, the monthly MoM history is compounded
 *     instead: (1 + r1)(1 + r2)...(1 + r12) - 1. The monthly history parsing
 *     (ASP.NET dates, percentage strings, stale feeds) is delegated to the
 *     existing {@link StatBureauInflationClient} so the logic lives in one
 *     place.</li>
 * </ol>
 * Note: the Ukraine and Euro area series stop at 2019-12 / 2020-12, so annual
 * requests beyond the cached range legitimately answer with no data.
 */
@Slf4j
@Component
public class StatBureauYearlyInflationClient {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final RestClient restClient;
    private final StatBureauApiProperties properties;
    private final StatBureauInflationClient statBureauInflationClient;

    public StatBureauYearlyInflationClient(RestClient.Builder builder,
                                           StatBureauApiProperties properties,
                                           StatBureauInflationClient statBureauInflationClient) {
        this.properties = properties;
        this.statBureauInflationClient = statBureauInflationClient;

        this.restClient = builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 SFM/1.0")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE + ", text/plain, */*")
                .build();
    }

    /**
     * Fetches the annual (calendar-year) inflation rate for the given currency's
     * economy, trying the calculator endpoint first and compounding the monthly
     * history as fallback.
     *
     * @param currency application currency whose economy is queried
     *                 (UAH &rarr; ukraine, EUR &rarr; eurozone, USD &rarr; united-states)
     * @param year     calendar year (e.g. 2019); for the current year the result
     *                 is the year-to-date rate because December has not closed yet
     * @return the parsed annual record, or {@code null} when StatBureau has no
     *         usable data for that currency and year
     */
    public YearlyInflationStatBureauRecordDto getAnnualInflation(Currency currency, int year) {
        if (currency == null || currency.getCountrySlug() == null) {
            log.warn("Cannot fetch annual inflation: currency or country slug is null");
            return null;
        }

        // 1. Primary approach: StatBureau calculator endpoint (Jan 1 to Dec 1)
        BigDecimal calculated = fetchCalculatedAnnualRate(currency, year);
        if (calculated != null) {
            return new YearlyInflationStatBureauRecordDto(year, calculated, null);
        }

        // 2. Mathematical fallback: compound (1 + r1)(1 + r2)...(1 + r12) - 1 from monthly data
        log.info("StatBureau calculator returned no annual rate for {} {}. Falling back to monthly compounding.",
                currency, year);
        BigDecimal compounded = computeAnnualRateFromMonthly(currency, year);
        if (compounded == null) {
            log.warn("No annual inflation available from StatBureau for {} in year {}", currency, year);
            return null;
        }
        return new YearlyInflationStatBureauRecordDto(year, compounded, null);
    }

    /**
     * Calls {@code calculate-inflation-rate-json} for the Jan 1 &rarr; Dec 1
     * window of the year. Sanitizes the quoted percentage string
     * (quotes, commas and '%' symbols) before BigDecimal conversion.
     */
    private BigDecimal fetchCalculatedAnnualRate(Currency currency, int year) {
        String startDate = year + "/1/1";
        String endDate = year + "/12/1";

        try {
            String rawResponse = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(properties.getYearlyPath())
                            .queryParam("country", currency.getCountrySlug())
                            .queryParam("start", startDate)
                            .queryParam("end", endDate)
                            .build())
                    .retrieve()
                    .body(String.class);

            if (rawResponse == null || rawResponse.isBlank() || "null".equalsIgnoreCase(rawResponse.trim())) {
                log.warn("Empty response from StatBureau annual calculator for {} ({} to {})",
                        currency, startDate, endDate);
                return null;
            }

            BigDecimal rate = parseRateString(rawResponse);
            if (rate == null) {
                // Typically the ASP.NET error page shown for years outside the data range
                log.warn("StatBureau annual calculator returned a non-numeric payload for {} ({} to {})",
                        currency, startDate, endDate);
                return null;
            }
            if (rate.compareTo(BigDecimal.ZERO) == 0) {
                // For years outside the cached series StatBureau answers with the literal
                // string "0" instead of an error, so an exact-zero result is treated as
                // "no data" and the monthly compounding fallback gets a chance to rescue
                // the year. A real calendar year of CPI inflation is never exactly 0%.
                log.info("StatBureau annual calculator returned 0 for {} ({} to {}) - "
                        + "treating it as no data for that window", currency, startDate, endDate);
                return null;
            }
            return rate;

        } catch (Exception ex) {
            log.warn("StatBureau calculate-inflation-rate-json failed for {} ({} to {}): {}",
                    currency, startDate, endDate, ex.getMessage());
            return null;
        }
    }

    /**
     * Compounds the twelve monthly MoM rates of the requested year into one
     * annual rate, reusing the monthly client's parsing.
     */
    private BigDecimal computeAnnualRateFromMonthly(Currency currency, int year) {
        List<MonthlyInflationStatBureauRecordDto> history = statBureauInflationClient.getHistoricalInflation(currency);
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

    /**
     * Mirrors the monthly client's string-to-rate sanitization so both clients
     * interpret StatBureau payloads identically.
     */
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
            log.warn("Failed to parse numeric annual inflation value from string '{}': {}", raw, ex.getMessage());
            return null;
        }
    }
}
