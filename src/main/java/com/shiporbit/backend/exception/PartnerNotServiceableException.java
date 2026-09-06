package com.shiporbit.backend.exception;

/** Thrown when a valid, known delivery partner cannot service a particular shipment. */
public class PartnerNotServiceableException extends RuntimeException {
    public PartnerNotServiceableException(String message) {
        super(message);
    }
}
