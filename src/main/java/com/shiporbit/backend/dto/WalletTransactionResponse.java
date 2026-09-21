package com.shiporbit.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record WalletTransactionResponse(
        UUID id,
        String type,
        String status,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String gateway,
        String gatewayTxnId,
        String referenceId,
        String remarks,
        LocalDateTime createdAt
) {
}
