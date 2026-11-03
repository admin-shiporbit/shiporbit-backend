package com.shiporbit.backend.rate.routing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * UPS Rating API (OAuth2 client-credentials). routerUrl is
 * https://wwwcie.ups.com for the test (CIE) environment and
 * https://onlinetools.ups.com for production.
 *
 * accountNumber (6-char UPS shipper number) is optional: without it UPS returns
 * published rates; with it, negotiated (contract) rates are requested as well.
 */
@ConfigurationProperties(prefix = "rate-aggregator.ups")
public record UpsConfigProperties(
        String clientId,
        String clientSecret,
        String accountNumber,
        String routerUrl,
        String tokenEndpoint,
        String ratingEndpoint,
        String apiVersion,
        String transactionSource,
        String shipperName,
        String shipperAddressLine,
        String shipperCity,
        int connectTimeoutSeconds,
        int readTimeoutSeconds
) {
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    public boolean hasAccountNumber() {
        return accountNumber != null && !accountNumber.isBlank();
    }
}
