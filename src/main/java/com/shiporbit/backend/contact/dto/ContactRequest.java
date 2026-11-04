package com.shiporbit.backend.contact.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Contact form submitted from the website. POST /api/v1/contact/email (public).
 * name, email and message are required; everything else is optional.
 * Single-line fields reject line breaks: name and topic go into the team email's subject line.
 * website: the old JSON name "webstie" is still accepted (@JsonAlias) so the current frontend keeps working.
 */
public record ContactRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[^\\r\\n]*", message = "must be a single line") String name,
        @NotBlank @Email @Size(max = 150) String email,
        @Size(max = 150) @Pattern(regexp = "[^\\r\\n]*", message = "must be a single line") String company,
        @Size(max = 20) @Pattern(regexp = "[0-9+()\\- ]*", message = "may contain only digits, spaces, +, -, ( and )") String phone,
        @Size(max = 100) @Pattern(regexp = "[^\\r\\n]*", message = "must be a single line") String topic,
        @Size(max = 50) @Pattern(regexp = "[^\\r\\n]*", message = "must be a single line") String volume,
        @NotBlank @Size(max = 5000) String message,
        @JsonAlias("webstie") @Size(max = 200) @Pattern(regexp = "[^\\r\\n]*", message = "must be a single line") String website
) {
    public ContactRequest {
        name = trim(name);
        email = email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
        company = trim(company);
        phone = trim(phone);
        topic = trim(topic);
        volume = trim(volume);
        message = message == null ? null : message.strip();
        website = trim(website);
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
