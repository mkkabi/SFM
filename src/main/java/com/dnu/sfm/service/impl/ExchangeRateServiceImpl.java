package com.dnu.sfm.service.impl;

import com.dnu.sfm.model.ExchangeRate;
import com.dnu.sfm.service.ExchangeRateService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ExchangeRateServiceImpl implements ExchangeRateService {

    private final ObjectMapper objectMapper;

    @Override
    public List<ExchangeRate> parseRates(String jsonPayload) {
        try {
            return objectMapper.readValue(jsonPayload, new TypeReference<List<ExchangeRate>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse exchange rate JSON", e);
        }
    }
}
