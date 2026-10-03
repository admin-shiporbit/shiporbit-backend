package com.shiporbit.backend.notification.channel;

import com.shiporbit.backend.notification.constants.Channel;
import com.shiporbit.backend.notification.dto.NotificationRequest;
import com.shiporbit.backend.notification.dto.NotificationResponse;

public interface NotificationChannel {

    Channel notificationChannelType();

    NotificationResponse send(NotificationRequest request);
}
