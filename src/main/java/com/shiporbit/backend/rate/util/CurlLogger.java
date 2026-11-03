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
 * before calling toCurl().
 *
 * Authorization headers are always masked here (scheme + first 6 characters kept), for every
 * partner. Bearer tokens used to be printed in full so the curl was directly runnable, but a
 * live token in a log file can be replayed until it expires - and log files get shared.
 * To replay a logged curl, paste a fresh token into it.
 */
public final class CurlLogger {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CurlLogger() {
    }

    public static String toCurl(String method, String url, Map<String, String> headers, Map<String, Object> body) {
        StringBuilder curl = new StringBuilder("curl --request ").append(method)
                .append(" --url '").append(url).append("'");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            String value = "Authorization".equalsIgnoreCase(header.getKey())
                    ? maskAuthorization(header.getValue()) : header.getValue();
            curl.append(" \\\n  --header '").append(header.getKey()).append(": ").append(value).append("'");
        }
        if (body != null) {
            curl.append(" \\\n  --data '").append(OBJECT_MAPPER.writeValueAsString(body)).append("'");
        }
        return curl.toString();
    }

    /** "Bearer eyJhbGciOi..." -> "Bearer eyJhbG...***REDACTED***". Keeps the scheme so the log still shows the auth type. */
    static String maskAuthorization(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        int space = value.indexOf(' ');
        String scheme = space > 0 ? value.substring(0, space + 1) : "";
        String credential = space > 0 ? value.substring(space + 1) : value;
        String visible = credential.length() > 12 ? credential.substring(0, 6) : "";
        return scheme + visible + "...***REDACTED***";
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
