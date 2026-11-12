package com.dnu.sfm.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration for the StatBureau.org API used by
 * {@link com.dnu.sfm.client.StatBureauInflationClient}.
 * <p>
 * Values are bound from {@code application.properties} (prefix {@code statbureau.api})
 * and carry the same defaults as the hard-coded client, so the feature works even
 * when the properties are not present.
 * <p>
 * Note: StatBureau keeps the United States series current, while the Ukraine and
 * Euro area series stop at 2019-12 / 2020-12 respectively - the UI surfaces the
 * newest stored month so stale feeds are visible to the user.
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
}
