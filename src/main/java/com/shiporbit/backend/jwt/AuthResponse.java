package com.shiporbit.backend.jwt;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
