package com.shiporbit.backend.notification.channel;

import com.shiporbit.backend.exception.NotificationException;
import com.shiporbit.backend.notification.constants.Channel;
import com.shiporbit.backend.notification.dto.NotificationRequest;
import com.shiporbit.backend.notification.dto.NotificationResponse;
import com.shiporbit.backend.notification.dto.RenderedMessage;
import com.shiporbit.backend.notification.provider.EmailProvider;
import com.shiporbit.backend.notification.repository.NotificationRepository;
import com.shiporbit.backend.notification.service.TemplateService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true")
public class EmailChannel extends AbstractChannel {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final EmailProvider emailProvider;

    public EmailChannel(TemplateService templateService, NotificationRepository logRepo, EmailProvider emailProvider) {
        super(templateService, logRepo);
        this.emailProvider = emailProvider;
    }

    @Override
    public Channel notificationChannelType() {
        return Channel.EMAIL;
    }

    @Override
    protected void validate(NotificationRequest request) {
        if (request.recipient() == null || !EMAIL_PATTERN.matcher(request.recipient()).matches()) {
            throw new NotificationException("Invalid email recipient");
        }
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            throw new NotificationException("idempotencyKey is required");
        }
    }

    @Override
    protected NotificationResponse doSend(NotificationRequest request, RenderedMessage message) {
        String subject = message.subject() != null ? message.subject() : request.purpose().getDesc();
        return emailProvider.send(request.recipient(), subject, message.body());
    }

    @Override
    protected String providerName() {
        return emailProvider.name();
    }
}
