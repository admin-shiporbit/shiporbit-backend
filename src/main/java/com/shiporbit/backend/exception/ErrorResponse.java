package com.shiporbit.backend.exception;

import com.shiporbit.backend.error.ErrorCode;
import com.shiporbit.backend.logging.Flow;

import java.time.LocalDateTime;

/**
 * Error body for every failed request.
 * errorId: unique per occurrence, also in the log line. code: stable ErrorCode code (e.g. SO-SVC-001).
 * error: HTTP reason phrase. status: HTTP status. traceId: the request's trace_id (search the log with it).
 */
public record ErrorResponse(
        String errorId,
        String traceId,
        String code,
        String error,
        int status,
        String message,
        LocalDateTime timestamp
) {
    public static ErrorResponse of(String errorId, ErrorCode errorCode, String message) {
        return new ErrorResponse(errorId, Flow.traceId(), errorCode.code(), errorCode.httpStatus().getReasonPhrase(),
                errorCode.httpStatus().value(), message == null ? errorCode.defaultMessage() : message,
                LocalDateTime.now());
    }
}
