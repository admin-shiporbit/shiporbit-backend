package com.shiporbit.backend.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;
import java.util.UUID;

/**
 * One recipient of a multi-recipient email.
 *
 * @param vars per-recipient template values (e.g. name); override the shared vars
 */
public record EmailRecipient(
        @NotBlank @Email String email,
        UUID userId,
        Map<String, String> vars) {

    public EmailRecipient {
        vars = vars == null ? Map.of() : Map.copyOf(vars);
    }
}
