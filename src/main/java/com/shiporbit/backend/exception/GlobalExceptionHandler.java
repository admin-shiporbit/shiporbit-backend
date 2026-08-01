package com.shiporbit.backend.exception;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.View;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final View error;

    public GlobalExceptionHandler(View error) {
        this.error = error;
    }


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
}
