package com.shiporbit.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record TopUpRequest(

        @NotNull
        @DecimalMin(value = "1.00", message = "Top-up amount must be at least 1.00")
        BigDecimal amount,

        // PayU's hosted checkout requires a contact number; Users has no phone field today,
        // so the caller supplies it per top-up rather than us inventing a placeholder value.
        @NotBlank
        @Pattern(regexp = "\\d{10}", message = "Phone number must contain 10 digits")
        String phoneNumber
) {
}
