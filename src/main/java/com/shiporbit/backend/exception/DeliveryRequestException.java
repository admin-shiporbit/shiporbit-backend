package com.shiporbit.backend.exception;

import com.shiporbit.backend.error.ErrorCode;

/** Invalid shipment input for the legacy /rate aggregator endpoint (SO-RATE-001). */
public class DeliveryRequestException extends ShipOrbitException {

    public DeliveryRequestException(String message) {
        super(ErrorCode.INVALID_SHIPMENT, message);
    }

    public DeliveryRequestException(String message, Throwable cause) {
        super(ErrorCode.INVALID_SHIPMENT, message, cause);
    }
}
