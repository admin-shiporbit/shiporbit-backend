package com.shiporbit.backend.rate.routing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rate-aggregator.shreemurti")
public record ShreeMurtiConfigProperties(
        String userName,
        String password,
        String routerUrl,
        String loginEndPoint,
        String refreshTokenEndPoint,
        String rateCalculatorEndpoint,
        String signinType,
        String userId,
        String tenantId,
        String apiKey
) {
}
