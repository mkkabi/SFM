package com.dnu.sfm.service;

import com.dnu.sfm.dto.AccountCurrencyComparisonDto;

public interface CurrencyAnalyticsService {

    AccountCurrencyComparisonDto compareCurrencyOutcomes(Long accountId);
}