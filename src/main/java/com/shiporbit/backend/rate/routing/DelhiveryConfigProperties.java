package com.shiporbit.backend.rate.routing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rate-aggregator.delhivery")
public record DelhiveryConfigProperties(
        String username,
        String password,
        String routerUrl,
        String loginEndpoint,
        String pincodeEndpoint,
        String ratecalculatorEndpoint
        ) {
}
