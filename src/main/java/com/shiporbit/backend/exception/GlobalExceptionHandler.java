package com.shiporbit.backend.exception;

import com.shiporbit.backend.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Maps exceptions to ErrorResponse {errorId, code, error, status, message, timestamp}.
 * All business exceptions extend ShipOrbitException and are handled by one method; the HTTP status
 * comes from their ErrorCode. Only framework exceptions need their own handler here.
 * Logging: 5xx -> ERROR with stack trace, 4xx -> WARN without.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ShipOrbitException.class)
    public ResponseEntity<ErrorResponse> shipOrbit(ShipOrbitException ex) {
        return respond(ex.errorCode(), ex.getMessage(), ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validateError(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));
        if (message.isBlank()) {   // class-level (@AssertTrue) errors
            message = ex.getBindingResult().getAllErrors().stream()
                    .map(e -> e.getDefaultMessage()).collect(Collectors.joining(", "));
        }
        return respond(ErrorCode.VALIDATION_FAILED, message, null);
    }

    // Malformed JSON or a value that cannot be parsed (e.g. unknown enum like "paymentMode": "CASH").
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadableBody(HttpMessageNotReadableException ex) {
        return respond(ErrorCode.MALFORMED_REQUEST, ex.getMostSpecificCause().getMessage(), null);
    }

    // @Validated controller parameters, e.g. @Pattern on @RequestParam (RateCardController /lane).
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> parameterValidationError(HandlerMethodValidationException ex) {
        String message = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> result.getMethodParameter().getParameterName() + ": " + error.getDefaultMessage()))
                .collect(Collectors.joining(", "));
        return respond(ErrorCode.VALIDATION_FAILED, message, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> authenticationError(AuthenticationException ex) {
        return respond(ErrorCode.INVALID_CREDENTIALS, ErrorCode.INVALID_CREDENTIALS.defaultMessage(), null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> invalidArgument(IllegalArgumentException ex) {
        return respond(ErrorCode.INVALID_ARGUMENT, ex.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> internalError(Exception ex) {
        // Do not leak internal exception messages to clients.
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), ex);
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode code, String message, Throwable ex) {
        String errorId = UUID.randomUUID().toString();
        if (code.httpStatus().is5xxServerError()) {
            LOGGER.error("Error id {} [{}] {}", errorId, code.code(), message, ex);
        } else {
            LOGGER.warn("Error id {} [{}] {}", errorId, code.code(), message);
        }
        return ResponseEntity.status(code.httpStatus()).body(ErrorResponse.of(errorId, code, message));
    }
}
