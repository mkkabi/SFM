package com.dnu.sfm.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;

public class ExchangeRateParser {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public static List<ExchangeRate> fromJsonList(String jsonArrayString) throws Exception {
        return MAPPER.readValue(jsonArrayString, new TypeReference<List<ExchangeRate>>() {});
    }
}