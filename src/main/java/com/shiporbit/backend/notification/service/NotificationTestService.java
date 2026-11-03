package com.shiporbit.backend.notification.service;

import com.shiporbit.backend.exception.NotificationException;
import com.shiporbit.backend.notification.channel.EmailChannel;
import com.shiporbit.backend.notification.constants.Channel;
import com.shiporbit.backend.notification.dto.BulkEmailResponse;
import com.shiporbit.backend.notification.dto.NotificationRequest;
import com.shiporbit.backend.notification.dto.NotificationResponse;
import com.shiporbit.backend.notification.dto.TestBulkEmailRequest;
import com.shiporbit.backend.notification.dto.TestEmailRequest;
import com.shiporbit.backend.notification.dto.TestTemplateEmailRequest;
import com.shiporbit.backend.notification.provider.EmailProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/** Admin-only helpers to verify email delivery. Not used by business flows. */
@Slf4j
@Service
public class NotificationTestService {

    private static final String DEFAULT_SUBJECT = "ShipOrbit test email";

    // ObjectProvider: these beans are conditional, so the app must still start when email is disabled.
    private final ObjectProvider<EmailProvider> emailProvider;
    private final ObjectProvider<EmailChannel> emailChannel;
    private final NotificationService notificationService;

    public NotificationTestService(ObjectProvider<EmailProvider> emailProvider,
                                   ObjectProvider<EmailChannel> emailChannel,
                                   NotificationService notificationService) {
        this.emailProvider = emailProvider;
        this.emailChannel = emailChannel;
        this.notificationService = notificationService;
    }

    public NotificationResponse sendRaw(TestEmailRequest request) {
        EmailProvider provider = emailProvider.getIfAvailable();
        if (provider == null) {
            throw new NotificationException("No email provider configured (check notification.email.provider)");
        }
        String subject = isBlank(request.subject()) ? DEFAULT_SUBJECT : request.subject();
        String body = isBlank(request.body())
                ? "<p>Test email from ShipOrbit backend.</p><p>Sent at " + Instant.now() + "</p>"
                : request.body();
        try {
            return provider.send(request.to(), subject, body);
        } catch (NotificationException e) {
            // Return the failure so the caller can see the SMTP error instead of a generic 4xx/5xx.
            log.warn("Test email via {} failed: {}", provider.name(), e.getMessage());
            return NotificationResponse.failed(provider.name(), e.getMessage());
        }
    }

    public NotificationResponse sendTemplate(TestTemplateEmailRequest request) {
        EmailChannel channel = emailChannel.getIfAvailable();
        if (channel == null) {
            throw new NotificationException("Email channel disabled (set notification.email.enabled=true)");
        }
        NotificationRequest notification = new NotificationRequest(
                null,
                request.to(),
                Channel.EMAIL,
                request.purpose(),
                request.vars(),
                "TEST:" + request.purpose() + ":EMAIL:" + UUID.randomUUID());
        return channel.send(notification);
    }

    public BulkEmailResponse sendTemplateToAll(TestBulkEmailRequest request) {
        return BulkEmailResponse.of(notificationService.sendEmailToAll(
                request.recipients(), request.purpose(), request.vars(),
                "TEST:" + request.purpose() + ":" + UUID.randomUUID()));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
