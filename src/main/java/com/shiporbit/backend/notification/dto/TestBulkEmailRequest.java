package com.shiporbit.backend.notification.dto;

import com.shiporbit.backend.notification.constants.NotificationPurpose;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * @param vars shared template values applied to every recipient
 */
public record TestBulkEmailRequest(
        @NotEmpty @Size(max = NotificationLimits.MAX_EMAIL_RECIPIENTS) List<@Valid EmailRecipient> recipients,
        @NotNull NotificationPurpose purpose,
        Map<String, String> vars) {
}
