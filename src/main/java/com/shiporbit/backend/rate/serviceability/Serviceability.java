package com.shiporbit.backend.rate.serviceability;

import com.shiporbit.backend.error.ErrorCode;

/**
 * Result of a partner serviceability check.
 * UNKNOWN = the partner could not answer (API error, timeout, unrecognised response).
 * LivePartnerQuoteProvider fetches a rate only on SERVICEABLE; NOT_SERVICEABLE and UNKNOWN both skip the rate call.
 * errorCode: null when SERVICEABLE; an SO-SVC-* / SO-PTR-* code otherwise.
 */
public record Serviceability(Status status, ErrorCode errorCode, String reason) {

    public enum Status { SERVICEABLE, NOT_SERVICEABLE, UNKNOWN }

    public static Serviceability yes(String reason) {
        return new Serviceability(Status.SERVICEABLE, null, reason);
    }

    public static Serviceability no(ErrorCode errorCode, String reason) {
        return new Serviceability(Status.NOT_SERVICEABLE, errorCode, reason);
    }

    public static Serviceability unknown(ErrorCode errorCode, String reason) {
        return new Serviceability(Status.UNKNOWN, errorCode, reason);
    }

    public boolean notServiceable() {
        return status == Status.NOT_SERVICEABLE;
    }

    /** Only definite answers are cached; UNKNOWN is retried on the next request. */
    public boolean cacheable() {
        return status != Status.UNKNOWN;
    }

    /** Both ends must pass: first NOT_SERVICEABLE wins, then UNKNOWN, else SERVICEABLE. */
    public static Serviceability both(Serviceability origin, Serviceability destination) {
        if (origin.notServiceable()) return origin;
        if (destination.notServiceable()) return destination;
        if (origin.status == Status.UNKNOWN) return origin;
        if (destination.status == Status.UNKNOWN) return destination;
        return yes(origin.reason + "; " + destination.reason);
    }

    /** Maps an HTTP status from a serviceability API call to the matching SO-PTR code. */
    public static ErrorCode codeForHttp(int status) {
        if (status == 401 || status == 403) return ErrorCode.PARTNER_AUTH_FAILED;
        if (status >= 400 && status < 500) return ErrorCode.PARTNER_REJECTED_REQUEST;
        return ErrorCode.PARTNER_UNAVAILABLE;
    }
}
