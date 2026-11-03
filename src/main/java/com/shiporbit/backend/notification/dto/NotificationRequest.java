package com.shiporbit.backend.notification.dto;

import com.shiporbit.backend.notification.constants.Channel;
import com.shiporbit.backend.notification.constants.NotificationPurpose;

import java.util.Map;
import java.util.UUID;

/**
 * @param channel        preferred channel; null lets routing decide
 * @param vars           template placeholder values, e.g. {"name": "Saurabh", "otp": "123456"}
 * @param idempotencyKey unique per logical message and channel, e.g. "ORDER:{id}:DELIVERED:EMAIL"
 */
public record NotificationRequest(
        UUID userId,
        String recipient,
        Channel channel,
        NotificationPurpose purpose,
        Map<String, String> vars,
        String idempotencyKey) {

    public NotificationRequest {
        vars = vars == null ? Map.of() : Map.copyOf(vars);
    }
}
