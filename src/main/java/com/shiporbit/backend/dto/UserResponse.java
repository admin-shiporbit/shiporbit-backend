package com.shiporbit.backend.dto;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String role,
        boolean enabled) {
}
