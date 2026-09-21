package com.shiporbit.backend.dto;

import java.util.Map;

/** Frontend auto-submits an HTML form with these fields (POST) to actionUrl to open PayU's hosted checkout. */
public record TopUpInitiationResponse(
        String referenceId,
        String actionUrl,
        Map<String, String> formFields
) {
}
