package com.shiporbit.backend.rate.serviceability;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Helpers for serviceability responses whose exact shape is not yet confirmed
 * (Delhivery B2B /pincode-service, Shree Maruti /serviceability/*). Pure, unit-tested.
 */
public final class ServiceabilityJson {

    /** Keys that carry a yes/no serviceability flag, searched anywhere in the response. */
    public static final Set<String> FLAG_KEYS = Set.of(
            "serviceable", "isServiceable", "is_serviceable", "serviceability", "isserviceable");

    private ServiceabilityJson() {
    }

    /** Depth-first search for the first FLAG_KEYS value that reads as a boolean. Empty = not found. */
    public static Optional<Boolean> findFlag(Object node) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (FLAG_KEYS.contains(String.valueOf(e.getKey()))) {
                    Optional<Boolean> b = asBoolean(e.getValue());
                    if (b.isPresent()) return b;
                }
            }
            for (Object v : map.values()) {
                Optional<Boolean> b = findFlag(v);
                if (b.isPresent()) return b;
            }
        } else if (node instanceof List<?> list) {
            // Any element serviceable => serviceable (e.g. a list of carriers).
            Optional<Boolean> result = Optional.empty();
            for (Object v : list) {
                Optional<Boolean> b = findFlag(v);
                if (b.isPresent()) {
                    if (b.get()) return b;
                    result = b;
                }
            }
            return result;
        }
        return Optional.empty();
    }

    /** true/false, "Y"/"N", "YES"/"NO", "true"/"false". Anything else = empty. */
    public static Optional<Boolean> asBoolean(Object v) {
        if (v instanceof Boolean b) return Optional.of(b);
        if (v instanceof String s) {
            return switch (s.trim().toUpperCase()) {
                case "Y", "YES", "TRUE" -> Optional.of(true);
                case "N", "NO", "FALSE" -> Optional.of(false);
                default -> Optional.empty();
            };
        }
        return Optional.empty();
    }

    public static boolean isYes(Object v) {
        return asBoolean(v).orElse(false);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object v) {
        return v instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    public static List<?> asList(Object v) {
        return v instanceof List<?> l ? l : List.of();
    }
}
