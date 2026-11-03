package com.dnu.sfm.client;

import com.dnu.sfm.model.ExchangeRate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class NbuExchangeRateClient {

    private final RestClient restClient;

    public NbuExchangeRateClient(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("https://bank.gov.ua/NBUStatService/v1/statdirectory")
                .build();
    }

    public List<ExchangeRate> getRatesForDate(LocalDate date) {
        String formattedDate = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/exchangenew")
                        .queryParam("date", formattedDate)
                        .queryParam("json", "")
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<ExchangeRate>>() {});
    }
}