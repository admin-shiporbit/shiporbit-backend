package com.shiporbit.backend.notification.dto;

import com.shiporbit.backend.notification.constants.NotificationStatus;

public record NotificationResponse(
        NotificationStatus status,
        String provider,
        String providerMessageId,
        String error) {

    public static NotificationResponse sent(String provider, String providerMessageId) {
        return new NotificationResponse(NotificationStatus.SENT, provider, providerMessageId, null);
    }

    public static NotificationResponse failed(String provider, String error) {
        return new NotificationResponse(NotificationStatus.FAILED, provider, null, error);
    }

    public boolean isSuccess() {
        return status == NotificationStatus.SENT || status == NotificationStatus.DELIVERED;
    }
}
