package com.shiporbit.backend.notification.provider;

import com.shiporbit.backend.notification.dto.NotificationResponse;

public interface EmailProvider {

    /** Vendor name stored in the notification log, e.g. "SMTP" or "SES". */
    String name();

    NotificationResponse send(String to, String subject, String body);
}
