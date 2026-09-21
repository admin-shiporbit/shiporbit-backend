package com.shiporbit.backend.payment.dto;

import java.util.Map;

/** Everything the frontend needs to auto-submit a hosted-checkout form to the gateway. */
public record PaymentInitiationResult(
        String actionUrl,
        Map<String, String> formFields
) {
}
