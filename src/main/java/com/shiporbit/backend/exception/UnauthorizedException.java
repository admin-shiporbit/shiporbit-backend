package com.shiporbit.backend.exception;

import com.shiporbit.backend.error.ErrorCode;

/** Error code ErrorCode.UNAUTHORIZED. */
public class UnauthorizedException extends ShipOrbitException {

    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }
}
