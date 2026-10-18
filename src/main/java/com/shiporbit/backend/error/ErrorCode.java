package com.shiporbit.backend.error;

import com.shiporbit.backend.rate.core.QuoteStatus;
import org.springframework.http.HttpStatus;

/**
 * Every error ShipOrbit returns, with a stable code for the frontend and logs.
 * Code format: SO-<AREA>-<NNN>. Codes never change meaning once released; add new ones instead.
 * <ul>
 *   <li>httpStatus: used when the error ends the whole request (GlobalExceptionHandler).</li>
 *   <li>Per-partner failures inside a quote do NOT fail the request; they appear in the response's
 *       unavailable list with the same code.</li>
 * </ul>
 */
public enum ErrorCode {

    // ---- general ----
    INTERNAL_ERROR("SO-GEN-000", HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error"),
    VALIDATION_FAILED("SO-GEN-001", HttpStatus.BAD_REQUEST, "Request validation failed"),
    MALFORMED_REQUEST("SO-GEN-002", HttpStatus.BAD_REQUEST, "Request body is malformed"),
    INVALID_ARGUMENT("SO-GEN-003", HttpStatus.BAD_REQUEST, "Invalid argument"),

    // ---- auth / user ----
    UNAUTHORIZED("SO-AUTH-001", HttpStatus.UNAUTHORIZED, "A valid Bearer token is required"),
    INVALID_CREDENTIALS("SO-AUTH-002", HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    EMAIL_ALREADY_EXISTS("SO-AUTH-003", HttpStatus.CONFLICT, "Email already registered"),
    ADDRESS_NOT_FOUND("SO-ADDR-001", HttpStatus.NOT_FOUND, "Address not found"),
    NOTIFICATION_FAILED("SO-NOTIF-001", HttpStatus.BAD_REQUEST, "Notification could not be sent"),

    // ---- rate calculation ----
    INVALID_SHIPMENT("SO-RATE-001", HttpStatus.BAD_REQUEST, "Shipment details are invalid"),
    PARTNER_DISABLED("SO-RATE-002", HttpStatus.UNPROCESSABLE_ENTITY, "Partner or service is disabled"),
    RATE_CARD_INCOMPLETE("SO-RATE-003", HttpStatus.INTERNAL_SERVER_ERROR, "Rate card configuration is incomplete"),
    ZONE_UNKNOWN("SO-RATE-004", HttpStatus.UNPROCESSABLE_ENTITY, "Zone could not be resolved for this lane"),
    WEIGHT_LIMIT_EXCEEDED("SO-RATE-005", HttpStatus.UNPROCESSABLE_ENTITY, "Weight exceeds the partner limit"),
    COD_NOT_AVAILABLE("SO-RATE-006", HttpStatus.UNPROCESSABLE_ENTITY, "COD not available for this service"),
    DISTANCE_REQUIRED("SO-RATE-007", HttpStatus.UNPROCESSABLE_ENTITY, "Distance is required for hyperlocal"),
    RATE_UNAVAILABLE("SO-RATE-008", HttpStatus.UNPROCESSABLE_ENTITY, "No rate available for this shipment"),

    // ---- serviceability ----
    NOT_SERVICEABLE("SO-SVC-001", HttpStatus.UNPROCESSABLE_ENTITY, "Pincode not serviceable"),
    PICKUP_NOT_AVAILABLE("SO-SVC-002", HttpStatus.UNPROCESSABLE_ENTITY, "Pickup not available at origin pincode"),
    COD_NOT_SERVICEABLE("SO-SVC-003", HttpStatus.UNPROCESSABLE_ENTITY, "COD not available at destination pincode"),
    PREPAID_NOT_SERVICEABLE("SO-SVC-004", HttpStatus.UNPROCESSABLE_ENTITY, "Prepaid not available at destination pincode"),
    PINCODE_EMBARGOED("SO-SVC-005", HttpStatus.UNPROCESSABLE_ENTITY, "Pincode under embargo"),
    SERVICEABILITY_UNCONFIRMED("SO-SVC-006", HttpStatus.BAD_GATEWAY, "Partner serviceability could not be confirmed"),
    HYPERLOCAL_ADDRESS_REQUIRED("SO-SVC-007", HttpStatus.BAD_REQUEST, "Hyperlocal needs address, pincode, latitude and longitude for both ends"),

    // ---- partner APIs ----
    PARTNER_AUTH_FAILED("SO-PTR-001", HttpStatus.BAD_GATEWAY, "Partner rejected our credentials"),
    PARTNER_REJECTED_REQUEST("SO-PTR-002", HttpStatus.BAD_GATEWAY, "Partner rejected the request"),
    PARTNER_UNAVAILABLE("SO-PTR-003", HttpStatus.BAD_GATEWAY, "Partner API returned a server error"),
    PARTNER_UNREACHABLE("SO-PTR-004", HttpStatus.BAD_GATEWAY, "Partner API unreachable"),
    PARTNER_TIMEOUT("SO-PTR-005", HttpStatus.GATEWAY_TIMEOUT, "Partner API timed out"),
    PARTNER_BAD_RESPONSE("SO-PTR-006", HttpStatus.BAD_GATEWAY, "Partner API returned an unexpected response"),
    PARTNER_NOT_CONFIGURED("SO-PTR-007", HttpStatus.SERVICE_UNAVAILABLE, "Partner credentials are not configured");

    private final String code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(String code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    /** Default code for a rate-card / quote status; live partners pass a more specific one. */
    public static ErrorCode forQuoteStatus(QuoteStatus status) {
        return switch (status) {
            case OK -> null;
            case VALIDATION_ERROR -> INVALID_SHIPMENT;
            case DISABLED -> PARTNER_DISABLED;
            case CONFIG_INCOMPLETE -> RATE_CARD_INCOMPLETE;
            case ZONE_UNKNOWN -> ZONE_UNKNOWN;
            case NOT_SERVICEABLE -> NOT_SERVICEABLE;
            case COD_NOT_AVAILABLE -> COD_NOT_AVAILABLE;
            case WEIGHT_LIMIT_EXCEEDED -> WEIGHT_LIMIT_EXCEEDED;
            case RATE_UNAVAILABLE -> RATE_UNAVAILABLE;
            case DISTANCE_REQUIRED -> DISTANCE_REQUIRED;
            case PARTNER_API_ERROR -> PARTNER_UNAVAILABLE;
        };
    }
}
