package com.shiporbit.backend.exception;

/** Thrown when an operation targets a wallet that hasn't been created for the user yet. */
public class WalletNotFoundException extends RuntimeException {
    public WalletNotFoundException(String message) {
        super(message);
    }
}
