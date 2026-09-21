package com.shiporbit.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record WalletBalanceResponse(
        UUID walletId,
        UUID userId,
        BigDecimal balance,
        LocalDateTime updatedAt
) {
}
