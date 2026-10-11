package com.shiporbit.backend.notification.channel;

import com.shiporbit.backend.notification.dto.NotificationRequest;
import com.shiporbit.backend.notification.dto.NotificationResponse;
import com.shiporbit.backend.notification.dto.RenderedMessage;
import com.shiporbit.backend.notification.entity.NotificationLog;
import com.shiporbit.backend.notification.repository.NotificationRepository;
import com.shiporbit.backend.notification.service.TemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

import java.util.Optional;

@Slf4j
public abstract class AbstractChannel implements NotificationChannel {

    private final TemplateService templateService;
    private final NotificationRepository logRepo;

    protected AbstractChannel(TemplateService templateService, NotificationRepository logRepo) {
        this.templateService = templateService;
        this.logRepo = logRepo;
    }

    @Override
    public final NotificationResponse send(NotificationRequest request) {
        // Invalid input is a caller error: throw to the caller, nothing is sent or logged.
        validate(request);

        Optional<NotificationLog> existing = logRepo.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            log.info("Duplicate notification skipped, idempotencyKey={}", request.idempotencyKey());
            return toResponse(existing.get());
        }

        RenderedMessage message = templateService.render(
                request.purpose(), notificationChannelType(), request.vars());

        NotificationResponse response;
        try {
            response = doSend(request, message);
        } catch (RuntimeException e) {
            // Deliberate recovery boundary: any provider failure becomes a FAILED result
            // so it is logged and the caller can fall back to another channel.
            log.warn("{} send failed via {}: {}", notificationChannelType(), providerName(), e.getMessage());
            response = NotificationResponse.failed(providerName(), e.getMessage());
        }
        if (response == null) {
            response = NotificationResponse.failed(providerName(), "Provider returned no response");
        }

        saveLog(request, response);
        return response;
    }

    // A failed log write must not turn a successful send into a failure (caller would retry and duplicate).
    private void saveLog(NotificationRequest request, NotificationResponse response) {
        try {
            logRepo.save(NotificationLog.from(request, notificationChannelType(), response));
        } catch (DataAccessException e) {
            log.error("Failed to save notification log, idempotencyKey={}", request.idempotencyKey(), e);
        }
    }

    private NotificationResponse toResponse(NotificationLog log) {
        return new NotificationResponse(log.getStatus(), log.getProvider(), log.getProviderMsgId(), log.getError());
    }

    protected abstract void validate(NotificationRequest request);

    protected abstract NotificationResponse doSend(NotificationRequest request, RenderedMessage message);

    protected abstract String providerName();
}
