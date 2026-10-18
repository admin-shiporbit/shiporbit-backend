package com.shiporbit.backend.rate.serviceability;

/**
 * Result of a partner serviceability check.
 * UNKNOWN = the partner could not answer (API error, timeout, unrecognised response). Callers treat it
 * as "go ahead and price" (fail-open) so a flaky serviceability API never hides a partner; the rate
 * call itself then decides.
 */
public record Serviceability(Status status, String reason) {

    public enum Status { SERVICEABLE, NOT_SERVICEABLE, UNKNOWN }

    public static Serviceability yes(String reason) {
        return new Serviceability(Status.SERVICEABLE, reason);
    }

    public static Serviceability no(String reason) {
        return new Serviceability(Status.NOT_SERVICEABLE, reason);
    }

    public static Serviceability unknown(String reason) {
        return new Serviceability(Status.UNKNOWN, reason);
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
}
