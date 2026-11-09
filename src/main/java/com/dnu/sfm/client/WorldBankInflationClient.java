package com.dnu.sfm.client;

import com.dnu.sfm.model.Currency;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class WorldBankInflationClient {

    private final RestClient restClient;

    public WorldBankInflationClient(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("https://api.worldbank.org/v2")
                .build();
    }

    public BigDecimal fetchAnnualInflation(Currency currency, int year) {
        String countryCode = switch (currency) {
            case UAH -> "UA";
            case EUR -> "XC"; // Euro Area
            case USD -> "US";
        };

        try {
            // World Bank JSON response: [metadata_obj, [ {date: "2024", value: 12.3}, ... ]]
            List<Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/country/{country}/indicator/FP.CPI.TOTL.ZG")
                            .queryParam("date", String.valueOf(year))
                            .queryParam("format", "json")
                            .build(countryCode))
                    .retrieve()
                    .body(List.class);

            if (response != null && response.size() > 1 && response.get(1) instanceof List<?> dataList) {
                if (!dataList.isEmpty() && dataList.get(0) instanceof Map<?, ?> dataMap) {
                    Object val = dataMap.get("value");
                    if (val != null) {
                        return new BigDecimal(val.toString());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch World Bank inflation for {}-{}: {}", currency, year, e.getMessage());
        }
        return null;
    }
}