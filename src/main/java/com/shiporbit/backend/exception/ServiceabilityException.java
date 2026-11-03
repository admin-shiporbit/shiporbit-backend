package com.shiporbit.backend.exception;

import com.shiporbit.backend.error.ErrorCode;

/**
 * A partner cannot serve (or could not confirm) a lane. Use one of the SO-SVC-* codes.
 * The quote flow does not throw this (a non-serviceable partner is just listed as unavailable);
 * it is for flows where serviceability is mandatory, e.g. booking with a chosen partner.
 */
public class ServiceabilityException extends ShipOrbitException {

    public ServiceabilityException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
