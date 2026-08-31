package com.shiporbit.backend.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AddressResponse(
        UUID id,
        String label,
        UUID userId,
        String isdCode,
        String phoneNumber,
        String addressLine1,
        String addressLine2,
        String city,
        Integer stateId,
        String stateCode,
        String stateName,
        String pinCode,
        boolean isDefault,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
