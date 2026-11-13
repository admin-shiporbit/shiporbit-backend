package com.shiporbit.backend.rate.serviceability;

import com.shiporbit.backend.error.ErrorCode;

/**
 * Result of a partner serviceability check.
 * UNKNOWN = the partner could not answer (API error, timeout, unrecognised response).
 * LivePartnerQuoteProvider fetches a rate only on SERVICEABLE; NOT_SERVICEABLE and UNKNOWN both skip the rate call.
 * errorCode: null when SERVICEABLE; an SO-SVC-* / SO-PTR-* code otherwise.
 */
// Serviceability answer: status, error code (null when serviceable), reason text.
public record Serviceability(Status status, ErrorCode errorCode, String reason) {

    // Three outcomes.
    public enum Status { SERVICEABLE, NOT_SERVICEABLE, UNKNOWN }

    // SERVICEABLE factory.
    public static Serviceability yes(String reason) {
        // No error code.
        return new Serviceability(Status.SERVICEABLE, null, reason);
    }

    // NOT_SERVICEABLE factory.
    public static Serviceability no(ErrorCode errorCode, String reason) {
        // With error code.
        return new Serviceability(Status.NOT_SERVICEABLE, errorCode, reason);
    }

    // UNKNOWN factory.
    public static Serviceability unknown(ErrorCode errorCode, String reason) {
        // With error code.
        return new Serviceability(Status.UNKNOWN, errorCode, reason);
    }

    // Is it NOT_SERVICEABLE?
    public boolean notServiceable() {
        // Check.
        return status == Status.NOT_SERVICEABLE;
    }

    /** Only definite answers are cached; UNKNOWN is retried on the next request. */
    // Cache only definite answers.
    public boolean cacheable() {
        // Not UNKNOWN.
        return status != Status.UNKNOWN;
    }

    /** Both ends must pass: first NOT_SERVICEABLE wins, then UNKNOWN, else SERVICEABLE. */
    // Combine origin + destination:
    public static Serviceability both(Serviceability origin, Serviceability destination) {
        // origin not served -> that,
        if (origin.notServiceable()) return origin;
        // destination not served -> that,
        if (destination.notServiceable()) return destination;
        // origin unknown -> that,
        if (origin.status == Status.UNKNOWN) return origin;
        // destination unknown -> that,
        if (destination.status == Status.UNKNOWN) return destination;
        // both served -> SERVICEABLE with both reasons.
        return yes(origin.reason + "; " + destination.reason);
    }

    /** Maps an HTTP status from a serviceability API call to the matching SO-PTR code. */
    // HTTP status -> error code:
    public static ErrorCode codeForHttp(int status) {
        // 401 / 403 -> PARTNER_AUTH_FAILED,
        if (status == 401 || status == 403) return ErrorCode.PARTNER_AUTH_FAILED;
        // other 4xx -> PARTNER_REJECTED_REQUEST,
        if (status >= 400 && status < 500) return ErrorCode.PARTNER_REJECTED_REQUEST;
        // 5xx -> PARTNER_UNAVAILABLE.
        return ErrorCode.PARTNER_UNAVAILABLE;
    }
}
