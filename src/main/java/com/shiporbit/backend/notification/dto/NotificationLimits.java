package com.shiporbit.backend.notification.dto;

public final class NotificationLimits {

    /** Max recipients per multi-recipient call; keeps one request well under Gmail's daily quota. */
    public static final int MAX_EMAIL_RECIPIENTS = 100;

    private NotificationLimits() {
    }
}
