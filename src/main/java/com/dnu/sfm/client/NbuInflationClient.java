package com.dnu.sfm.client;

import com.dnu.sfm.dto.NbuInflationRecordDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class NbuInflationClient {

    private final RestClient restClient;

    public NbuInflationClient(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("https://bank.gov.ua/NBUStatService/v1/statdirectory")
                .build();
    }

    /**
     * Fetches monthly Consumer Price Index (CPI) history for Ukraine directly from NBU.
     */
    public List<NbuInflationRecordDto> getUkraineMonthlyInflation() {
        try {
            log.info("Requesting Ukraine monthly inflation from NBU StatService...");
            List<NbuInflationRecordDto> records = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/inflation")
                            .queryParam("period", "m")
                            .queryParam("json", "")
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<NbuInflationRecordDto>>() {});

            if (records == null || records.isEmpty()) {
                log.warn("NBU returned 0 inflation records.");
                return Collections.emptyList();
            }

            log.info("Successfully fetched {} inflation records from NBU.", records.size());
            return records;

        } catch (Exception ex) {
            log.error("Failed to fetch inflation from NBU: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }
}