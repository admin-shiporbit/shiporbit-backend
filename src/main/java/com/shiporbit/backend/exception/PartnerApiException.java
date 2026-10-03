package com.shiporbit.backend.exception;

import org.springframework.web.client.HttpStatusCodeException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * An external rate-partner API call failed (network, auth, or bad response).
 * Shared across every partner (Delhivery, ShreeMurti, future ones) - not partner-specific
 * despite the generic error-body shapes tried in from() below; use from(e, partnerName)
 * (or the message/cause constructors directly) rather than adding a per-partner subclass.
 */
public class PartnerApiException extends RuntimeException {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final int httpStatus;

    public PartnerApiException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public PartnerApiException(String message, Throwable cause, int httpStatus) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }

    // Shared by every rate partner (Delhivery, ShreeMurti, future ones) via
    // from(e, partnerName) below - each partner's error body shape differs, so this
    // tries every known shape rather than assuming Delhivery's. partnerName only ever
    // ends up in the final fallback message, when no known shape matched at all.
    public static PartnerApiException from(HttpStatusCodeException e, String partnerName) {
        try {
            Map<String, Object> body = OBJECT_MAPPER.readValue(e.getResponseBodyAsString(), Map.class);
            String message = extractMessage(body);
            if (message != null) {
                return new PartnerApiException(message, e.getStatusCode().value());
            }
        } catch (Exception ignored) {
            // Response body wasn't parseable JSON at all - fall through to the raw-body
            // fallback below.
        }
        return new PartnerApiException(
                partnerName + " call failed (" + e.getStatusCode() + "): " + e.getResponseBodyAsString(),
                e.getStatusCode().value());
    }

    private static String extractMessage(Map<String, Object> body) {
        // Delhivery: {"error": {"code", "message": "..."}}
        if (body.get("error") instanceof Map<?, ?> error && error.get("message") != null) {
            return error.get("message").toString();
        }
        // ShreeMurti (and most flat error shapes): {"status_code", "message": "...", "trace_id"}
        if (body.get("message") != null) {
            return body.get("message").toString();
        }
        return null;
    }
}