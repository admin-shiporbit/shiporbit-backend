package com.shiporbit.backend.exception;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> duplicateEmail(EmailAlreadyExistsException ex){
        String errorId = UUID.randomUUID().toString();
        LOGGER.warn("Error id {}, with message {}",errorId,ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(
                errorId
                ,"Conflict"
                ,HttpStatus.CONFLICT.value()
                ,ex.getMessage()
                , LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validateError(MethodArgumentNotValidException ex){
        String errorId = UUID.randomUUID().toString();
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));
        LOGGER.warn("Error ID {} , with message {}",errorId,message);
        ErrorResponse errorResponse = new ErrorResponse(
                errorId
                ,"Validation Failed"
                ,HttpStatus.BAD_REQUEST.value()
                ,message
                ,LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> authenticationError(AuthenticationException ex) {
        String errorId = UUID.randomUUID().toString();
        LOGGER.warn("Authentication failed with error ID {}", errorId);
        ErrorResponse errorResponse = new ErrorResponse(
                errorId,
                "Unauthorized",
                HttpStatus.UNAUTHORIZED.value(),
                "Invalid email or password",
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> internalError(Exception ex){
        String errorId = UUID.randomUUID().toString();
        LOGGER.error("Error id {} with error {}",errorId,ex);
        ErrorResponse errorResponse = new ErrorResponse(
                errorId
                ,"Internal Error"
                ,HttpStatus.INTERNAL_SERVER_ERROR.value()
                ,ex.getMessage()
                ,LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }


    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> unauthorizedError(UnauthorizedException ex){
        String errorId = UUID.randomUUID().toString();
        LOGGER.warn("Unauthorized request with error ID {}: {}", errorId, ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(
                errorId,
                "Unauthorized",
                HttpStatus.UNAUTHORIZED.value(),
                ex.getMessage(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    @ExceptionHandler(AddressNotFoundException.class)
    public ResponseEntity<ErrorResponse> addressNotFound(AddressNotFoundException ex) {
        String errorId = UUID.randomUUID().toString();
        LOGGER.warn("Address not found with error ID {}: {}", errorId, ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(
                errorId,
                "Not Found",
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(PartnerNotServiceableException.class)
    public ResponseEntity<ErrorResponse> partnerNotServiceable(PartnerNotServiceableException ex) {
        String errorId = UUID.randomUUID().toString();
        LOGGER.warn("Partner not serviceable with error ID {}: {}", errorId, ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(
                errorId,
                "Unprocessable Entity",
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ex.getMessage(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(errorResponse);
    }

    @ExceptionHandler(DelhiveryApiException.class)
    public ResponseEntity<ErrorResponse> delhiveryApiFailure(DelhiveryApiException ex) {
        String errorId = UUID.randomUUID().toString();
        LOGGER.error("Delhivery API call failed with error ID {}: {}", errorId, ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(
                errorId,
                "Bad Gateway",
                HttpStatus.BAD_GATEWAY.value(),
                ex.getMessage(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> invalidArgument(IllegalArgumentException ex) {
        String errorId = UUID.randomUUID().toString();
        LOGGER.warn("Invalid request with error ID {}: {}", errorId, ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(
                errorId,
                "Bad Request",
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(DeliveryRequestException.class)
    public ResponseEntity<ErrorResponse> deliveryRequestxception(DeliveryRequestException errorResponse) {
        String errorId = UUID.randomUUID().toString();
        LOGGER.error("Delivery request with error ID {}: {}", errorId, errorResponse);
        ErrorResponse response =
                new ErrorResponse(
                        errorId,
                        "Invalid Request",
                        HttpStatus.BAD_REQUEST.value(),
                        errorResponse.getMessage(),
                        LocalDateTime.now()
                        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
