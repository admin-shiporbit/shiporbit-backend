package com.shiporbit.backend.exception;

/** Thrown when a wallet debit is attempted for more than the current available balance. */
public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}
