package com.shiporbit.backend.exception;

import com.shiporbit.backend.error.ErrorCode;

/** Error code ErrorCode.EMAIL_ALREADY_EXISTS. */
public class EmailAlreadyExistsException extends ShipOrbitException {

    public EmailAlreadyExistsException(String message) {
        super(ErrorCode.EMAIL_ALREADY_EXISTS, message);
    }
}
