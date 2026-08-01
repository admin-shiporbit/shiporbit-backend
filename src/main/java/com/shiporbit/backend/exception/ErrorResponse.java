package com.shiporbit.backend.exception;

import java.time.LocalDateTime;

public record ErrorResponse(
        String errorId,
        String error,
        int status,
        String message,
        LocalDateTime timestamp
) {
}
