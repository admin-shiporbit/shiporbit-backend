package com.shiporbit.backend.exception;

import com.shiporbit.backend.error.ErrorCode;

/** Error code ErrorCode.ADDRESS_NOT_FOUND. */
public class AddressNotFoundException extends ShipOrbitException {

    public AddressNotFoundException(String message) {
        super(ErrorCode.ADDRESS_NOT_FOUND, message);
    }
}
