package com.shiporbit.backend.payment.routing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.payu")
public record PayUConfigProperties(
        String merchantKey,
        String merchantSalt,
        String baseUrl,
        String paymentEndpoint,
        String successUrl,
        String failureUrl
) {
}
