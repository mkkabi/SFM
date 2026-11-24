package com.dnu.sfm.service;

import com.dnu.sfm.dto.AnnualInflationScatterSeriesDto;

/**
 * Business logic for the StatBureau annual inflation scatter chart of the US
 * economy (USD only): assembles the annual consumer price inflation rate of
 * the United States for the requested number of past years, serving stored
 * database rows first and automatically fetching any missing year from the
 * StatBureau API, which is then persisted for future use.
 */
public interface StatBureauInflationChartService {

    /**
     * Builds the annual inflation series of the USD economy for the last
     * {@code numberOfYears} completed calendar years. The running year is
     * always excluded: its annual (Dec-to-Dec) rate is not final before the
     * year ends (StatBureau would answer with the year-to-date figure).
     *
     * <p>For every year in the window the database is checked first. When the
     * stored annual row of the {@code USD} currency is missing, the year is
     * fetched from the StatBureau API and the fetched record is stored
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
