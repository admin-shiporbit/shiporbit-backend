package com.shiporbit.backend.exception;

import com.shiporbit.backend.error.ErrorCode;

/**
 * Base for every ShipOrbit business exception. GlobalExceptionHandler turns it into
 * ErrorResponse {errorId, code, error, status, message, timestamp} using errorCode's HTTP status.
 * New exceptions: extend this and pass an ErrorCode - no new @ExceptionHandler needed.
 */
public class ShipOrbitException extends RuntimeException {

    private final ErrorCode errorCode;

    public ShipOrbitException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), null);
    }

    public ShipOrbitException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public ShipOrbitException(ErrorCode errorCode, String message, Throwable cause) {
        super(message == null || message.isBlank() ? errorCode.defaultMessage() : message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
