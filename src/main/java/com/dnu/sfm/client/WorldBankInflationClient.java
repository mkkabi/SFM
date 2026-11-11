package com.dnu.sfm.client;

import com.dnu.sfm.config.WorldBankApiProperties;
import com.dnu.sfm.dto.WorldBankInflationRecordDto;
import com.dnu.sfm.model.Currency;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Client for the World Bank Open Data API v2
 * ({@code https://api.worldbank.org/v2/country/{country}/indicator/FP.CPI.TOTL.ZG}),
 * which reports annual consumer price inflation ("Inflation, consumer prices (annual %)")
 * for one economy and calendar year per request.
 * <p>
 * The raw JSON payload is a two-element array {@code [metadata_obj, [dataRow, ...]]};
 * the metadata object is skipped and the data rows are mapped to
 * {@link WorldBankInflationRecordDto} records.
 */
@Slf4j
@Component
public class WorldBankInflationClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final WorldBankApiProperties properties;

    public WorldBankInflationClient(RestClient.Builder builder,
                                    ObjectMapper objectMapper,
                                    WorldBankApiProperties properties) {
        this.restClient = builder
                .baseUrl(properties.getBaseUrl())
                .build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Maps an application currency to the World Bank economy code the annual
     * inflation is queried for.
     */
    public static String resolveCountryCode(Currency currency) {
        return switch (currency) {
            case UAH -> "UA";
            case EUR -> "XC"; // Euro Area
            case USD -> "US";
        };
    }

    /**
     * Fetches the annual inflation rows the World Bank publishes for the given
     * currency's economy and year. Usually a single row is returned; the list is
     * empty when the API has no data or the call failed.
     */
    public List<WorldBankInflationRecordDto> fetchAnnualInflationRecords(Currency currency, int year) {
        String countryCode = resolveCountryCode(currency);

        try {
            log.info("Requesting World Bank annual inflation for {} ({}) in {}",
                    currency, countryCode, year);

            // World Bank JSON response: [metadata_obj, [ {date: "2024", value: 12.3}, ... ]]
            List<Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/country/{country}/indicator/{indicator}")
                            .queryParam("date", String.valueOf(year))
                            .queryParam("format", "json")
                            .build(countryCode, properties.getIndicatorCode()))
                    .retrieve()
                    .body(List.class);

            if (response == null || response.size() < 2 || !(response.get(1) instanceof List<?> dataList)) {
                log.warn("World Bank returned an unexpected payload for {} in {}", currency, year);
                return Collections.emptyList();
            }

            List<WorldBankInflationRecordDto> records = objectMapper.convertValue(
                    dataList, new TypeReference<List<WorldBankInflationRecordDto>>() {});

            log.info("Fetched {} World Bank annual inflation record(s) for {} in {}",
                    records.size(), currency, year);
            return records;

        } catch (Exception e) {
            log.error("Failed to fetch World Bank inflation for {}-{}: {}", currency, year, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Single-value convenience accessor kept for backward compatibility:
     * returns the annual inflation rate (in %) for the currency's economy and
     * year, or {@code null} when no value is published.
     */
    public BigDecimal fetchAnnualInflation(Currency currency, int year) {
        return fetchAnnualInflationRecords(currency, year).stream()
                .filter(WorldBankInflationRecordDto::hasValue)
                .map(WorldBankInflationRecordDto::toRatePercent)
                .findFirst()
                .orElse(null);
    }
}
