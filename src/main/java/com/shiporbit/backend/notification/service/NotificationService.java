package com.shiporbit.backend.notification.service;

import com.shiporbit.backend.exception.NotificationException;
import com.shiporbit.backend.notification.channel.EmailChannel;
import com.shiporbit.backend.notification.config.NotificationExecutorConfig;
import com.shiporbit.backend.notification.constants.Channel;
import com.shiporbit.backend.notification.constants.NotificationPurpose;
import com.shiporbit.backend.notification.dto.EmailRecipient;
import com.shiporbit.backend.notification.dto.NotificationLimits;
import com.shiporbit.backend.notification.dto.NotificationRequest;
import com.shiporbit.backend.notification.dto.NotificationResponse;
import com.shiporbit.backend.notification.dto.RecipientResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class NotificationService {

    // ObjectProvider: EmailChannel is conditional, so the app must still start when email is disabled.
    private final ObjectProvider<EmailChannel> emailChannel;
    private final TaskExecutor executor;

    public NotificationService(ObjectProvider<EmailChannel> emailChannel,
                               @Qualifier(NotificationExecutorConfig.NOTIFICATION_EXECUTOR) TaskExecutor executor) {
        this.emailChannel = emailChannel;
        this.executor = executor;
    }

    /**
     * Sends one separate email per recipient in parallel and waits for all results.
     * Use when the caller needs per-recipient status (admin/test tools).
     *
     * @param sharedVars        template values common to all recipients
     * @param idempotencyPrefix identifies the logical message, e.g. "ORDER:123:DELIVERED";
     *                          the recipient and channel are appended per email
     */
    public List<RecipientResult> sendEmailToAll(List<EmailRecipient> recipients,
                                                NotificationPurpose purpose,
                                                Map<String, String> sharedVars,
                                                String idempotencyPrefix) {
        List<CompletableFuture<RecipientResult>> futures =
                submitAll(recipients, purpose, sharedVars, idempotencyPrefix);
        List<RecipientResult> results = futures.stream().map(CompletableFuture::join).toList();
        log.info("Multi-recipient email {}: {} recipients, {} sent",
                purpose, results.size(), results.stream().filter(r -> r.response().isSuccess()).count());
        return results;
    }

    /**
     * Fire-and-forget version for business flows (order updates): returns immediately,
     * results are recorded in so_notification_log.
     */
    public void sendEmailToAllInBackground(List<EmailRecipient> recipients,
                                           NotificationPurpose purpose,
                                           Map<String, String> sharedVars,
                                           String idempotencyPrefix) {
        List<CompletableFuture<RecipientResult>> futures =
                submitAll(recipients, purpose, sharedVars, idempotencyPrefix);
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenRun(() -> log.info("Background email {} done for {} recipients", purpose, futures.size()));
    }

    private List<CompletableFuture<RecipientResult>> submitAll(List<EmailRecipient> recipients,
                                                               NotificationPurpose purpose,
                                                               Map<String, String> sharedVars,
                                                               String idempotencyPrefix) {
        EmailChannel channel = emailChannel.getIfAvailable();
        if (channel == null) {
            throw new NotificationException("Email channel disabled (set notification.email.enabled=true)");
        }
        if (recipients == null || recipients.isEmpty()) {
            return List.of();
        }
        if (recipients.size() > NotificationLimits.MAX_EMAIL_RECIPIENTS) {
            throw new NotificationException("Too many recipients, max " + NotificationLimits.MAX_EMAIL_RECIPIENTS);
        }

        // Normalise and drop duplicate addresses, keeping the first occurrence.
        Map<String, EmailRecipient> unique = new LinkedHashMap<>();
        for (EmailRecipient recipient : recipients) {
            unique.putIfAbsent(recipient.email().trim().toLowerCase(Locale.ROOT), recipient);
        }

        return unique.entrySet().stream()
                .map(e -> CompletableFuture.supplyAsync(
                        () -> new RecipientResult(e.getKey(),
                                sendOne(channel, e.getValue(), e.getKey(), purpose, sharedVars, idempotencyPrefix)),
                        executor))
                .toList();
    }

    private NotificationResponse sendOne(EmailChannel channel, EmailRecipient recipient, String email,
                                         NotificationPurpose purpose, Map<String, String> sharedVars,
                                         String idempotencyPrefix) {
        Map<String, String> vars = new HashMap<>(sharedVars == null ? Map.of() : sharedVars);
        vars.putAll(recipient.vars());
        NotificationRequest request = new NotificationRequest(
                recipient.userId(), email, Channel.EMAIL, purpose, vars,
                idempotencyPrefix + ":" + email + ":" + Channel.EMAIL);
        try {
            return channel.send(request);
        } catch (RuntimeException e) {
            // Runs on a pool thread: never let one recipient's failure break the batch.
            log.warn("Email to {} not sent: {}", email, e.getMessage());
            return NotificationResponse.failed(null, e.getMessage());
        }
    }
}
