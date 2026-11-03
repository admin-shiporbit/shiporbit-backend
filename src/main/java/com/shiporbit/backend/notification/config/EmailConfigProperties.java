package com.shiporbit.backend.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.email")
public record EmailConfigProperties(
        boolean enabled,
        String provider,
        String from,
        String fromName,
        String replyTo
) {
}
