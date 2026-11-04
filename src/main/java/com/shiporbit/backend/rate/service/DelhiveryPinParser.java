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
final class DelhiveryPinParser {

    private DelhiveryPinParser() {
    }

    static Serviceability parse(Object response, String pin) {
        Map<String, Object> root = ServiceabilityJson.asMap(response);
        if (!root.containsKey("delivery_codes")) {
            return Serviceability.unknown(ErrorCode.SERVICEABILITY_UNCONFIRMED, "Delhivery B2C pincode response has no delivery_codes");
        }
        List<?> codes = ServiceabilityJson.asList(root.get("delivery_codes"));
        if (codes.isEmpty()) {
            return Serviceability.no(ErrorCode.NOT_SERVICEABLE, "Delhivery does not serve pincode " + pin);
        }
        Map<String, Object> pc = ServiceabilityJson.asMap(ServiceabilityJson.asMap(codes.get(0)).get("postal_code"));
        String remarks = String.valueOf(pc.getOrDefault("remarks", "")).toLowerCase(Locale.ROOT);
        if (remarks.contains("embargo")) {
            return Serviceability.no(ErrorCode.PINCODE_EMBARGOED, "Delhivery: pincode " + pin + " under embargo");
        }
        String flags = "pickup=" + yn(pc.get("pickup"))
                + ",cod=" + yn(pc.get("cod"))
                + ",pre_paid=" + yn(pc.get("pre_paid"))
                + ",is_oda=" + yn(pc.get("is_oda"));
        return Serviceability.yes(flags);
    }

    static boolean flag(String flags, String key) {
        for (String part : flags.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return "Y".equals(kv[1]);
            }
        }
        return false;
    }

    private static String yn(Object v) {
        return ServiceabilityJson.isYes(v) ? "Y" : "N";
    }
}
