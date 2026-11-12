package com.dnu.sfm.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration for the StatBureau.org API used by
 * {@link com.dnu.sfm.client.StatBureauInflationClient} (monthly history) and
 * {@link com.dnu.sfm.client.StatBureauYearlyInflationClient} (annual rates).
 * <p>
 * Values are bound from {@code application.properties} (prefix {@code statbureau.api})
 * and carry the same defaults as the hard-coded client, so the feature works even
 * when the properties are not present.
 * <p>
 * Note: StatBureau keeps the United States series current, while the Ukraine and
 * Euro area series stop at 2019-12 / 2020-12 respectively - the UI surfaces the
 * newest stored month/year so stale feeds are visible to the user.
 */
@Getter
@Setter
@NoArgsConstructor
@ConfigurationProperties(prefix = "statbureau.api")
public class StatBureauApiProperties {

    /**
     * Base URL of the StatBureau API.
     */
    private String baseUrl = "https://www.statbureau.org";

    /**
     * Path of the endpoint used to calculate the annual inflation rate between
     * two dates; called by the yearly client with {@code start=YYYY/1/1} and
     * {@code end=YYYY/12/1}. StatBureau's dedicated yearly history endpoint
     * ({@code get-yearly-inflation-data-json}) has been retired, so this
     * calculator endpoint is the primary source for annual figures.
     */
    private String yearlyPath = "/calculate-inflation-rate-json";
}
