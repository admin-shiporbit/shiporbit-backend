package com.shiporbit.backend.rate.routing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DHL Express MyDHL API (Basic auth with API key/secret issued via developer.dhl.com).
 * routerUrl is https://express.api.dhl.com/mydhlapi/test for test and
 * https://express.api.dhl.com/mydhlapi for production.
 * accountNumber is the DHL Express shipper account - rates are account-specific.
 */
@ConfigurationProperties(prefix = "rate-aggregator.dhl")
public record DhlConfigProperties(
        String apiKey,
        String apiSecret,
        String accountNumber,
        String routerUrl,
        String ratesEndpoint,
        String shipperCity,
        int connectTimeoutSeconds,
        int readTimeoutSeconds
) {
    public boolean isConfigured() {
        return notBlank(apiKey) && notBlank(apiSecret) && notBlank(accountNumber);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
