package com.dnu.sfm.config;

import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Shared configuration for every outgoing HTTP client (NBU exchange rates,
 * NBU inflation, StatBureau, World Bank).
 * <p>
 * Without an explicit request factory the JDK client uses no read timeout at
 * all, so one hung connection to bank.gov.ua would pin a Tomcat worker thread
 * indefinitely. This bean replaces Spring Boot's auto-configured
 * {@code RestClient.Builder} prototype with one that always applies sane
 * connect/read timeouts.
 * <p>
 * {@link WorldBankApiProperties} is enabled here so the World Bank client can
 * inject its base URL and indicator code from {@code application.properties}.
 */
@Configuration
@EnableConfigurationProperties(WorldBankApiProperties.class)
public class RestClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    @Scope("prototype")
    public RestClient.Builder restClientBuilder(RestClientBuilderConfigurer configurer) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return configurer.configure(RestClient.builder().requestFactory(requestFactory));
    }
}
