package com.shiporbit.backend.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Raw email sent straight through the provider; subject and body default when blank. */
public record TestEmailRequest(
        @NotBlank @Email String to,
        @Size(max = 200) String subject,
        @Size(max = 10_000) String body) {
}
