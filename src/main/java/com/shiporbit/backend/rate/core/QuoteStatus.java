package com.shiporbit.backend.rate.core;

/** Outcome of quoting one partner service. Everything except OK comes with a reason. */
public enum QuoteStatus {
    OK,
    VALIDATION_ERROR,
    DISABLED,
    CONFIG_INCOMPLETE,
    ZONE_UNKNOWN,
    NOT_SERVICEABLE,
    COD_NOT_AVAILABLE,
    WEIGHT_LIMIT_EXCEEDED,
    RATE_UNAVAILABLE,
    DISTANCE_REQUIRED,
    PARTNER_API_ERROR
}
