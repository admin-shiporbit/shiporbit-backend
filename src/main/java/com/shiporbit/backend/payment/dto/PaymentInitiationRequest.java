package com.shiporbit.backend.payment.dto;

import java.math.BigDecimal;

/** Gateway-agnostic request to start a payment; gateway implementations map this to their own field names. */
public record PaymentInitiationRequest(
        String referenceId,
        BigDecimal amount,
        String productInfo,
        String customerName,
        String customerEmail,
        String customerPhone
) {
}
