package com.dnu.sfm.service;

import com.dnu.sfm.dto.NbuInflationScatterSeriesDto;

/**
 * Business logic for the NBU annual inflation scatter chart: assembles the
 * headline national annual CPI rate for the requested number of past years,
 * serving stored database rows first and automatically fetching any missing
 * year from the NBU StatService API, which is then persisted for future use.
 */
public interface NbuInflationChartService {

    /**
     * Builds the annual inflation series for the last {@code numberOfYears}
     * completed calendar years. The running year is always excluded: its
     * annual (Dec-to-Dec) CPI rate is not published before the year ends.
     *
     * <p>For every year in the window the database is checked first. When the
     * headline annual CPI row (index type "DTPY_", commodity group "Total")
     * is missing, the year is fetched from the NBU API and the fetched records
     * are stored (upserted) so that subsequent requests are served from the
     * database without any remote call.</p>
     *
     * @param numberOfYears how many past years to plot (2, 5 or 10)
     * @return the series with per-point source labels and bookkeeping of which
     *         years were served from the DB, fetched from the API, or could not
     *         be resolved at all
     * @throws IllegalArgumentException if {@code numberOfYears} is not positive
     */
    NbuInflationScatterSeriesDto getAnnualInflationScatterData(int numberOfYears);
}
