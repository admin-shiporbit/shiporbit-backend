package com.shiporbit.backend.notification.service;

import com.shiporbit.backend.notification.constants.Channel;
import com.shiporbit.backend.notification.constants.NotificationPurpose;
import com.shiporbit.backend.notification.dto.RenderedMessage;

import java.util.Map;

public interface TemplateService {
    RenderedMessage render(NotificationPurpose purpose, Channel channel, Map<String, String> vars);
}
