package com.dnu.sfm.client;

import com.dnu.sfm.dto.NbuYearlyInflationRecordDto;
import com.dnu.sfm.dto.NbuMonthlyInflationRecordDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
     * https://bank.gov.ua/NBUStatService/v1/statdirectory/inflation?period=m&date=202301&json
     */
    public List<NbuMonthlyInflationRecordDto> getUkraineMonthlyInflation(LocalDate date) {
        String formattedDate = date.format(DateTimeFormatter.ofPattern("yyyyMM"));
        try {
            log.info("Requesting Ukraine monthly inflation from NBU StatService...");
            List<NbuMonthlyInflationRecordDto> records = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/inflation")
                            .queryParam("period", 'm')
                            .queryParam("date", formattedDate)
                            .queryParam("json", "")
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<NbuMonthlyInflationRecordDto>>() {});

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

    /**
     * Fetches monthly Consumer Price Index (CPI) history for Ukraine directly from NBU.
     * https://bank.gov.ua/NBUStatService/v1/statdirectory/inflation?period=m&date=202301&json
     */
    public List<NbuYearlyInflationRecordDto> getUkraineAnnualInflation(LocalDate date) {
        String formattedDate = date.format(DateTimeFormatter.ofPattern("yyyy"));
        try {
            log.info("Requesting Ukraine monthly inflation from NBU StatService...");
            List<NbuYearlyInflationRecordDto> records = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/inflation")
                            .queryParam("period", 'y')
                            .queryParam("date", formattedDate+"01")
                            .queryParam("json", "")
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<NbuYearlyInflationRecordDto>>() {});

            if (records == null || records.isEmpty()) {
                log.warn("NBU returned 0 inflation records.");
                return Collections.emptyList();
            }

            log.info("Successfully fetched {} Annual inflation records from NBU.", records.size());
            return records;

        } catch (Exception ex) {
            log.error("Failed to fetch inflation from NBU: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }
}