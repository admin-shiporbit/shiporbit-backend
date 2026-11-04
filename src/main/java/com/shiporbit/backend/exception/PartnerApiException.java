package com.shiporbit.backend.exception;

import com.shiporbit.backend.error.ErrorCode;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Map;

/**
 * An external partner API call failed (network, auth, or bad response).
 * Shared across every partner (Delhivery, ShreeMurti, UPS, DHL, future ones) - not partner-specific;
 * use from(e, partnerName) or the constructors rather than adding a per-partner subclass.
 * <p>
 * errorCode (SO-PTR-*) is worked out from what failed:
 * <ul>
 *   <li>from(HttpStatusCodeException): 401/403 -> PARTNER_AUTH_FAILED, other 4xx -> PARTNER_REJECTED_REQUEST,
 *       5xx -> PARTNER_UNAVAILABLE</li>
 *   <li>(message, cause, status): timeout in the cause chain -> PARTNER_TIMEOUT, partner HTTP error -> by its status,
 *       network error -> PARTNER_UNREACHABLE,
 *       anything else -> PARTNER_BAD_RESPONSE</li>
 *   <li>(message, status): 401/403 -> AUTH, 4xx -> REJECTED, 503 -> PARTNER_NOT_CONFIGURED, else PARTNER_BAD_RESPONSE</li>
 * </ul>
 * httpStatus() is the partner's status (or our 502/503 when there was none) - for logs only.
 */
public class PartnerApiException extends ShipOrbitException {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final int httpStatus;

    public PartnerApiException(ErrorCode errorCode, String message, Throwable cause, int httpStatus) {
        super(errorCode, message, cause);
        this.httpStatus = httpStatus;
    }

    public PartnerApiException(String message, int httpStatus) {
        this(codeForStatus(httpStatus, false), message, null, httpStatus);
    }

    public PartnerApiException(String message, Throwable cause, int httpStatus) {
        this(codeForCause(cause), message, cause, httpStatus);
    }

    public int httpStatus() {
        return httpStatus;
    }

    // Each partner's error body shape differs, so this tries every known shape rather than assuming
    // Delhivery's. partnerName only ends up in the fallback message, when no known shape matched.
    public static PartnerApiException from(HttpStatusCodeException e, String partnerName) {
        int status = e.getStatusCode().value();
        ErrorCode code = codeForStatus(status, true);
        try {
            Map<String, Object> body = OBJECT_MAPPER.readValue(e.getResponseBodyAsString(), Map.class);
            String message = extractMessage(body);
            if (message != null) {
                return new PartnerApiException(code, partnerName + ": " + message, e, status);
            }
        } catch (Exception ignored) {
            // Response body wasn't parseable JSON - fall through to the raw-body fallback.
        }
        return new PartnerApiException(code,
                partnerName + " call failed (" + e.getStatusCode() + "): " + e.getResponseBodyAsString(), e, status);
    }

    public static ErrorCode codeForStatus(int status, boolean fromPartner) {
        if (status == 401 || status == 403) return ErrorCode.PARTNER_AUTH_FAILED;
        if (status >= 400 && status < 500) return ErrorCode.PARTNER_REJECTED_REQUEST;
        if (!fromPartner && status == 503) return ErrorCode.PARTNER_NOT_CONFIGURED;
        if (fromPartner && status >= 500) return ErrorCode.PARTNER_UNAVAILABLE;
        return ErrorCode.PARTNER_BAD_RESPONSE;
    }

    public static ErrorCode codeForCause(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException) {
                return ErrorCode.PARTNER_TIMEOUT;
            }
        }
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (t instanceof HttpStatusCodeException http) {      // partner answered with an error status
                return codeForStatus(http.getStatusCode().value(), true);
            }
        }
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (t instanceof RestClientException || t instanceof IOException) {
                return ErrorCode.PARTNER_UNREACHABLE;
            }
        }
        return ErrorCode.PARTNER_BAD_RESPONSE;
    }

    private static String extractMessage(Map<String, Object> body) {
        // Delhivery: {"error": {"code", "message": "..."}}
        if (body.get("error") instanceof Map<?, ?> error && error.get("message") != null) {
            return error.get("message").toString();
        }
        // DHL MyDHL (RFC 7807 style): prefer "detail" ("message" there is only the status text).
        if (body.get("detail") != null) {
            return body.get("detail").toString();
        }
        // ShreeMurti and most flat shapes: {"status_code", "message": "...", "trace_id"}
        if (body.get("message") != null) {
            return body.get("message").toString();
        }
        return null;
    }
}
