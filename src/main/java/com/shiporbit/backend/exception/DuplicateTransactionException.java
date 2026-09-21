package com.shiporbit.backend.exception;

/** Thrown when a wallet operation is retried with a reference ID that already exists. */
public class DuplicateTransactionException extends RuntimeException {
    public DuplicateTransactionException(String message) {
        super(message);
    }
}
