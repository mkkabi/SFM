package com.dnu.sfm.service;

import com.dnu.sfm.model.ExchangeRate;

import java.util.List;

public interface ExchangeRateService {

    List<ExchangeRate> parseRates(String jsonPayload);

}