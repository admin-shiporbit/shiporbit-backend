package com.shiporbit.backend.exception;

import com.shiporbit.backend.error.ErrorCode;

/** Thrown when a valid, known delivery partner cannot service a particular shipment (SO-SVC-001 by default). */
public class PartnerNotServiceableException extends ServiceabilityException {

    public PartnerNotServiceableException(String message) {
        super(ErrorCode.NOT_SERVICEABLE, message);
    }

    public PartnerNotServiceableException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
