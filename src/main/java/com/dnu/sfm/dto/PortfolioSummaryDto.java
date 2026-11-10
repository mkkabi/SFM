package com.dnu.sfm.dto;

import com.dnu.sfm.model.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * View-layer snapshot of the total funds held across all accounts, valued in UAH.
 *
 * <p>Only the current principal is aggregated: for deposit accounts the
 * {@code balance} field never contains accrued interest (interest is tracked
 * separately in {@code DepositAccount.accruedInterest}), so the totals below
 * represent the current amount without interest.</p>
 *
 * @param balancesByCurrency principal total per {@link Currency} (always present,
 *                           {@code BigDecimal.ZERO} for currencies with no accounts)
 * @param ratesToUah         NBU rate used per currency, quoted as UAH per 1 unit
 *                           of the currency (e.g. USD -&gt; 41.50); UAH maps to 1.
 *                           May lack entries for currencies whose rate could not
 *                           be retrieved
 * @param totalInUah         grand total of all balances converted to UAH, or
 *                           {@code null} if at least one currency could not be
 *                           valued at the current NBU rate
 * @param ratesDate          the date for which the NBU rates were requested
 * @param accountCount       number of accounts included in the totals
 */
public record PortfolioSummaryDto(
        Map<Currency, BigDecimal> balancesByCurrency,
        Map<Currency, BigDecimal> ratesToUah,
        BigDecimal totalInUah,
        LocalDate ratesDate,
        long accountCount
) {}
