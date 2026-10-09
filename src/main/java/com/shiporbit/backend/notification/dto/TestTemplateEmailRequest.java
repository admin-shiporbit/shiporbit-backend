package com.shiporbit.backend.notification.dto;

import com.shiporbit.backend.notification.constants.NotificationPurpose;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/** Full pipeline test: template render, EmailChannel, provider and notification log. */
public record TestTemplateEmailRequest(
        @NotBlank @Email String to,
        @NotNull NotificationPurpose purpose,
        Map<String, String> vars) {
}
