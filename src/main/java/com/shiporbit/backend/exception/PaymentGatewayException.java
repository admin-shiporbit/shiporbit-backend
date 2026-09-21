package com.shiporbit.backend.exception;

/** A payment gateway (PayU today) call failed, or its response/callback could not be trusted. */
public class PaymentGatewayException extends RuntimeException {

    private final int httpStatus;

    public PaymentGatewayException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public PaymentGatewayException(String message, Throwable cause, int httpStatus) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
