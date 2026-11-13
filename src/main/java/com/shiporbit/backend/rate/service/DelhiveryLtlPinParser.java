package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.error.ErrorCode;
import com.shiporbit.backend.rate.serviceability.Serviceability;
import com.shiporbit.backend.rate.serviceability.ServiceabilityJson;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Delhivery LTL GET {router}/pincode-service/{pin}?weight={kg}. Pure, unit-tested.
 * Shapes seen in real responses (2026-11-09):
 * <pre>
 * 200 {"success":true,"data":{"b2b_fail_on_demand_pincodes":[],"pincode_serviceability_data":[
 *       {"pincode":201306,"payment_type":"[\"COD\", \"Pre-paid\", \"Cash\", \"REPL\", \"Pickup\"]","oda":false, ...}]}}
 * 400 {"success":false,"error":{"code":400,"message":"pin: 403519 is not serviceable."}}
 * </pre>
 * payment_type arrived as a JSON-array string; a real array is accepted too.
 * A served pin returns SERVICEABLE with reason "pickup=Y,cod=Y,pre_paid=Y,is_oda=N" (same format as
 * DelhiveryPinParser), so the caller applies origin / destination / payment-mode rules on one cached answer.
 */
final class DelhiveryLtlPinParser {

    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");

    private DelhiveryLtlPinParser() {
    }

    static Serviceability parse(Object response, String pin) {
        Map<String, Object> data = ServiceabilityJson.asMap(ServiceabilityJson.asMap(response).get("data"));
        if (!data.containsKey("pincode_serviceability_data")) {
            return Serviceability.unknown(ErrorCode.SERVICEABILITY_UNCONFIRMED,
                    "Delhivery LTL pincode-service: no pincode_serviceability_data in response");
        }
        for (Object item : ServiceabilityJson.asList(data.get("pincode_serviceability_data"))) {
            Map<String, Object> row = ServiceabilityJson.asMap(item);
            if (!pin.equals(String.valueOf(row.get("pincode")).trim())) {
                continue;
            }
            List<String> types = paymentTypes(row.get("payment_type"));
            return Serviceability.yes("pickup=" + yn(types.contains("pickup"))
                    + ",cod=" + yn(types.contains("cod"))
                    + ",pre_paid=" + yn(types.contains("pre-paid") || types.contains("prepaid"))
                    + ",is_oda=" + yn(ServiceabilityJson.isYes(row.get("oda"))));
        }
        // Not seen yet: a 200 without this pin. Not treated as "not serviceable" until Delhivery confirms.
        return Serviceability.unknown(ErrorCode.SERVICEABILITY_UNCONFIRMED,
                "Delhivery LTL pincode-service: pincode " + pin + " missing from response");
    }

    /** Delhivery answers "not serviceable" as HTTP 400 + message; null when the error means something else. */
    static Serviceability fromHttpError(int status, String body, String pin) {
        if (status == 400 && body != null && body.toLowerCase(Locale.ROOT).contains("not serviceable")) {
            return Serviceability.no(ErrorCode.NOT_SERVICEABLE, "Delhivery LTL does not serve pincode " + pin);
        }
        return null;
    }

    /** ["COD","Pre-paid",...] as a list or as a JSON-array string -> lower-case names. */
    static List<String> paymentTypes(Object value) {
        List<String> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            list.forEach(v -> out.add(String.valueOf(v).trim().toLowerCase(Locale.ROOT)));
        } else if (value != null) {
            Matcher m = QUOTED.matcher(value.toString());
            while (m.find()) {
                out.add(m.group(1).trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static String yn(boolean v) {
        return v ? "Y" : "N";
    }
}
