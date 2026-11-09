package com.dnu.sfm.exception;

import com.dnu.sfm.model.Currency;

public class InflationDataUnavailableException extends RuntimeException {

    public InflationDataUnavailableException(Currency currency, int year) {
        super("Unable to retrieve official inflation rate for " + currency + " in year " + year +
                ". External API is unreachable and no cached record exists.");
    }

    public InflationDataUnavailableException(Currency currency, int year, Throwable cause) {
        super("Unable to retrieve official inflation rate for " + currency + " in year " + year +
                ". External API call failed and no cached record exists.", cause);
    }
}