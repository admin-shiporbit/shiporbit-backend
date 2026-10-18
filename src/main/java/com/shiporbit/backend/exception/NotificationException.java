package com.shiporbit.backend.exception;

import com.shiporbit.backend.error.ErrorCode;

/** Error code ErrorCode.NOTIFICATION_FAILED. */
public class NotificationException extends ShipOrbitException {

    public NotificationException(String message) {
        super(ErrorCode.NOTIFICATION_FAILED, message);
    }
}
