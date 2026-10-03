package com.shiporbit.backend.rate.util;

import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds a copy-pasteable curl command for an outgoing partner API call, for local
 * debugging only - every caller logs the result at DEBUG, never anything enabled in
 * production. Shared across every rate partner on purpose (Delhivery, ShreeMurti,
 * future ones) - adding a new partner never requires touching this file.
 *
 * This class has no idea which fields are secret for a given partner - callers MUST
 * redact real credentials (passwords, refresh tokens) out of the body via redact()
 * before calling toCurl(). A short-lived bearer token in a header is fine to leave in -
 * that's the whole point, so the printed curl is directly runnable - but a password or
 * refresh token is a reusable credential and must never reach the logs.
 */
public final class CurlLogger {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CurlLogger() {
    }

    public static String toCurl(String method, String url, Map<String, String> headers, Map<String, Object> body) {
        StringBuilder curl = new StringBuilder("curl --request ").append(method)
                .append(" --url '").append(url).append("'");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            curl.append(" \\\n  --header '").append(header.getKey()).append(": ").append(header.getValue()).append("'");
        }
        if (body != null) {
            curl.append(" \\\n  --data '").append(OBJECT_MAPPER.writeValueAsString(body)).append("'");
        }
        return curl.toString();
    }

    /** Returns a copy of body with each of keysToRedact replaced - never mutates body itself. */
    public static Map<String, Object> redact(Map<String, Object> body, String... keysToRedact) {
        Map<String, Object> copy = new LinkedHashMap<>(body);
        for (String key : keysToRedact) {
            if (copy.containsKey(key)) {
                copy.put(key, "***REDACTED***");
            }
        }
        return copy;
    }
}
