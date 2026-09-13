package com.shiporbit.backend.exception;

public class DeliveryRequestException extends RuntimeException {

    public DeliveryRequestException(String message) {super(message);}

    public DeliveryRequestException(String message, Throwable cause) {super(message, cause);}
}
