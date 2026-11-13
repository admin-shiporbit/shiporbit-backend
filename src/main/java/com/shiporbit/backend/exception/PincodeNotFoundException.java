package com.shiporbit.backend.exception;

import com.shiporbit.backend.error.ErrorCode;

/** Error code ErrorCode.PINCODE_NOT_FOUND: the pincode is in no partner's pincode list (SO_PARTNER_PINCODE). */
public class PincodeNotFoundException extends ShipOrbitException {

    public PincodeNotFoundException(String message) {
        super(ErrorCode.PINCODE_NOT_FOUND, message);
    }
}
