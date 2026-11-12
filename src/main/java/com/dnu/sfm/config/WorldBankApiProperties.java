package com.dnu.sfm.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration for the World Bank Open Data API used by
 * {@link com.dnu.sfm.client.WorldBankInflationClient}.
 * <p>
 * Values are bound from {@code application.properties} (prefix {@code worldbank.api})
 * and carry the same defaults as the hard-coded client, so the feature works even
 * when the properties are not present.
 */
@Getter
@Setter
@NoArgsConstructor
@ConfigurationProperties(prefix = "worldbank.api")
public class WorldBankApiProperties {

    /**
     * Base URL of the World Bank API v2.
     */
    private String baseUrl = "https://api.worldbank.org/v2";

    /**
     * Indicator code for annual consumer price inflation
     * ("Inflation, consumer prices (annual %)").
     */
    private String indicatorCode = "FP.CPI.TOTL.ZG";
}
