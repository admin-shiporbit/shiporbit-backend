package com.shiporbit.backend.exception;

import org.springframework.web.client.HttpStatusCodeException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/** Delhivery's live freight-calculator API call failed (network, auth, or bad response). */
public class DelhiveryApiException extends RuntimeException {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final int httpStatus;

    public DelhiveryApiException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public DelhiveryApiException(String message, Throwable cause, int httpStatus) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public static DelhiveryApiException from(HttpStatusCodeException e) {
        try {
            Map<String, Object> body = OBJECT_MAPPER.readValue(e.getResponseBodyAsString(), Map.class);
            Map<String, Object> error = (Map<String, Object>) body.get("error");
            return new DelhiveryApiException(error.get("message").toString(), e.getStatusCode().value());
        } catch (Exception ex) {
            return new DelhiveryApiException(
                    "Delhivery call failed (" + e.getStatusCode() + "): " + e.getResponseBodyAsString(),
                    e.getStatusCode().value());
        }
    }
}