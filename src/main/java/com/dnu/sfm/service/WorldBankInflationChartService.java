package com.dnu.sfm.service;

import com.dnu.sfm.dto.AnnualInflationScatterSeriesDto;

/**
 * Business logic for the World Bank annual inflation scatter chart of the Euro
 * Area economy (EUR only): assembles the annual consumer price inflation rate
 * ("Inflation, consumer prices (annual %)", indicator FP.CPI.TOTL.ZG) of the
 * Euro Area for the requested number of past years, serving stored database
 * rows first and automatically fetching any missing year from the World Bank
 * Open Data API, which is then persisted for future use.
 */
public interface WorldBankInflationChartService {

    /**
     * Builds the annual inflation series of the EUR economy (Euro Area,
     * World Bank economy code "XC") for the last {@code numberOfYears}
     * completed calendar years. The running year is always excluded: the
     * World Bank publishes a year's annual figure only after the year is over
     * (and even then with some delay, so a recent year can legitimately be
     * unavailable).
     *
     * <p>For every year in the window the database is checked first. When the
     * stored annual row of the {@code EUR} currency is missing, the year is
     * fetched from the World Bank API and the fetched record is stored
     * (upserted) so that subsequent requests are served from the database
     * without any remote call.</p>
     *
     * @param numberOfYears how many past years to plot (2, 5 or 10)
     * @return the series with per-point source labels and bookkeeping of which
     *         years were served from the DB, fetched from the API, or could not
     *         be resolved at all
     * @throws IllegalArgumentException if {@code numberOfYears} is not positive
     */
    AnnualInflationScatterSeriesDto getAnnualInflationScatterData(int numberOfYears);
}
