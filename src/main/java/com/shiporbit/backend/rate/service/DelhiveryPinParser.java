package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.error.ErrorCode;
import com.shiporbit.backend.rate.serviceability.Serviceability;
import com.shiporbit.backend.rate.serviceability.ServiceabilityJson;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Delhivery B2C GET /c/api/pin-codes/json/?filter_codes=<pin>. Pure, unit-tested.
 * Shape (from Delhivery's API, not yet confirmed with a real 200 from your account):
 * {"delivery_codes":[{"postal_code":{"pin":560001,"pre_paid":"Y","cod":"Y","pickup":"Y","repl":"Y",
 *   "is_oda":"N","remarks":"", ...}}]}
 * Empty delivery_codes = Delhivery does not serve the pincode.
 * A served pin returns SERVICEABLE with reason "pickup=Y,cod=Y,pre_paid=Y,is_oda=N" - the caller applies
 * the origin / destination / payment-mode rule, so one cached lookup serves every rule.
 */
// Static parser (package-private).
final class DelhiveryPinParser {

    // No instances.
    private DelhiveryPinParser() {
    }

    // Pin-codes API answer -> Serviceability.
    static Serviceability parse(Object response, String pin) {
        // Root object (empty map if not an object).
        Map<String, Object> root = ServiceabilityJson.asMap(response);
        // No delivery_codes key?
        if (!root.containsKey("delivery_codes")) {
            // -> UNKNOWN (shape not understood).
            return Serviceability.unknown(ErrorCode.SERVICEABILITY_UNCONFIRMED, "Delhivery B2C pincode response has no delivery_codes");
        }
        // The list.
        List<?> codes = ServiceabilityJson.asList(root.get("delivery_codes"));
        // Empty?
        if (codes.isEmpty()) {
            // -> NOT_SERVICEABLE (Delhivery does not serve this pin).
            return Serviceability.no(ErrorCode.NOT_SERVICEABLE, "Delhivery does not serve pincode " + pin);
        }
        // First entry's postal_code object.
        Map<String, Object> pc = ServiceabilityJson.asMap(ServiceabilityJson.asMap(codes.get(0)).get("postal_code"));
        // Remarks, lower case.
        String remarks = String.valueOf(pc.getOrDefault("remarks", "")).toLowerCase(Locale.ROOT);
        // Embargo?
        if (remarks.contains("embargo")) {
            // -> NOT_SERVICEABLE PINCODE_EMBARGOED.
            return Serviceability.no(ErrorCode.PINCODE_EMBARGOED, "Delhivery: pincode " + pin + " under embargo");
        }
        // Flags text: pickup=Y/N
        String flags = "pickup=" + yn(pc.get("pickup"))
                // ,cod=Y/N
                + ",cod=" + yn(pc.get("cod"))
                // ,pre_paid=Y/N
                + ",pre_paid=" + yn(pc.get("pre_paid"))
                // ,is_oda=Y/N.
                + ",is_oda=" + yn(pc.get("is_oda"));
        // -> SERVICEABLE with the flags in reason (evaluate() applies origin / destination rules).
        return Serviceability.yes(flags);
    }

    // Read one flag from the text.
    static boolean flag(String flags, String key) {
        // Each "key=value" part:
        for (String part : flags.split(",")) {
            // split on '='.
            String[] kv = part.split("=", 2);
            // Key matches?
            if (kv.length == 2 && kv[0].equals(key)) {
                // true when Y.
                return "Y".equals(kv[1]);
            }
        }
        // Key not found -> false.
        return false;
    }

    // Y / N normaliser.
    private static String yn(Object v) {
        // Yes-like values ("Y", "true" ...) -> Y, else N.
        return ServiceabilityJson.isYes(v) ? "Y" : "N";
    }
}
