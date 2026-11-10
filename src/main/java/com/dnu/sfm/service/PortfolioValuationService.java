package com.dnu.sfm.service;

import com.dnu.sfm.dto.PortfolioSummaryDto;

/**
 * Aggregates the funds held across all accounts and values them in UAH
 * at the current NBU exchange rate.
 */
public interface PortfolioValuationService {

    /**
     * Sums the current balances of all accounts grouped by currency and
     * converts the grand total to UAH using the current NBU exchange rate.
     *
     * <p>Only the principal is counted: deposit account balances never include
     * accrued interest (interest is held separately in
     * {@code DepositAccount.accruedInterest}), so the returned totals represent
     * the current amount without interest.</p>
     *
     * @return the portfolio summary; {@code totalInUah} is {@code null} when the
     *         NBU rate could not be retrieved for at least one currency
     */
    PortfolioSummaryDto getPortfolioSummary();
}
