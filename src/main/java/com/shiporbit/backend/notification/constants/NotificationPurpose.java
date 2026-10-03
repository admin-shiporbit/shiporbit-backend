package com.shiporbit.backend.notification.constants;

import lombok.Getter;

@Getter
public enum NotificationPurpose {

    OTP(1, 1, "Login OTP"),
    ORDER(2, 1, "Order Created"),
    BOOKED(2, 2, "Order Booked"),
    OUT_FOR_DELIVERY(2, 3, "Order Out For Delivery"),
    DELIVERED(2, 4, "Order Delivered"),
    SIGN_UP(1, 2, "Sign Up OTP"),
    PASSWORD_RESET(1, 3, "Password Reset OTP");

    private final int id;
    private final int requestType;
    private final String desc;

    NotificationPurpose(int id, int requestType, String desc) {
        this.id = id;
        this.requestType = requestType;
        this.desc = desc;
    }
}
