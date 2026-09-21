package com.shiporbit.backend.payment.dto;

import java.math.BigDecimal;

/** Result of verifying a gateway callback: hash-checked and safe to act on. */
public record PaymentVerificationResult(
        boolean success,
        String referenceId,
        String gatewayTxnId,
        BigDecimal amount,
        String rawStatus
) {
}
