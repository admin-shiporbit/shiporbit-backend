package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.exception.PartnerApiException;
import com.shiporbit.backend.rate.dto.response.RateResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Delhivery B2C invoice-charges response -> RateResponse. Pure (no Spring), unit-tested.
 * <p>
 * Expected shape (NOT yet confirmed against a real staging response - check the DEBUG log line
 * "Delhivery B2C response" on the first call and adjust the keys here):
 * <pre>
 * [ { "charge_DL": .., "charge_FSC": .., "charge_COD": .., ...other charge_* ..,
 *     "gross_amount": .., "total_amount": .., "tax_data": {"IGST": .., "CGST": .., "SGST": .., ...},
 *     "zone": "..", "charged_weight": .. } ]
 * </pre>
 * Mapping: base = charge_DL, surcharge = charge_FSC (or charge_FS), every other non-zero charge_*
 * goes into handlingCharges, gst = sum of tax_data numbers (or total - gross when tax_data is absent),
 * finalFreight = total_amount. LivePartnerQuoteProvider puts any gap between the lines and
 * (total - gst) into an ADJUSTMENT line, so an unknown key never changes the price.
 */
// Static mapper (package-private).
final class DelhiveryB2cResponseMapper {

    // Keys that mean fuel surcharge.
    private static final Set<String> FUEL_KEYS = Set.of("charge_FSC", "charge_FS");

    // No instances.
    private DelhiveryB2cResponseMapper() {
    }

    // B2C answer -> RateResponse.
    static RateResponse map(Object response, String partnerCode, String partnerName) {
        // First row of the answer (array of rows, or one object).
        Map<String, Object> row = firstRow(response);
        // No total?
        if (row.get("total_amount") == null) {
            // -> PartnerApiException 502 (-> Unavailable).
            throw new PartnerApiException("Delhivery B2C response has no total_amount: " + row, 502);
        }
        // Partner total incl. GST.
        BigDecimal total = num(row.get("total_amount"));

        // Base = charge_DL.
        BigDecimal base = num(row.get("charge_DL"));
        // Fuel = charge_FSC or charge_FS, whichever exists,
        BigDecimal fuel = FUEL_KEYS.stream().filter(row::containsKey).findFirst()
                // else 0.
                .map(k -> num(row.get(k))).orElse(BigDecimal.ZERO);
        // Every other charge_* goes into handling.
        BigDecimal handling = BigDecimal.ZERO;
        // For each key:
        for (Map.Entry<String, Object> e : row.entrySet()) {
            // key name.
            String key = e.getKey();
            // charge_* but not base or fuel?
            if (key.startsWith("charge_") && !"charge_DL".equals(key) && !FUEL_KEYS.contains(key)) {
                // add to handling.
                handling = handling.add(num(e.getValue()));
            }
        }

        // Partner GST:
        BigDecimal gst;
        // tax_data present?
        if (row.get("tax_data") instanceof Map<?, ?> tax && !tax.isEmpty()) {
            // -> sum of its values (IGST / CGST / SGST ...);
            gst = tax.values().stream().map(DelhiveryB2cResponseMapper::num).reduce(BigDecimal.ZERO, BigDecimal::add);
        // else gross_amount present?
        } else if (row.get("gross_amount") != null) {
            // -> total - gross;
            gst = total.subtract(num(row.get("gross_amount")));
        // else
        } else {
            // 0.
            gst = BigDecimal.ZERO;
        }

        // RateResponse: base, fuel hike 0, fuel, ROV 0,
        return new RateResponse(partnerCode, partnerName, base, BigDecimal.ZERO, fuel, BigDecimal.ZERO,
                // ODA 0, handling, GST, total.
                BigDecimal.ZERO, handling, gst, total);
    }

    @SuppressWarnings("unchecked")
    // Pick the row.
    private static Map<String, Object> firstRow(Object response) {
        // Non-empty list with a map first?
        if (response instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> m) {
            // -> that map.
            return (Map<String, Object>) m;
        }
        // Single object?
        if (response instanceof Map<?, ?> m) {
            // Error bodies come back as an object, e.g. {"error": "..."} / {"detail": "..."}
            // Has a total?
            if (m.get("total_amount") != null) {
                // -> use it.
                return (Map<String, Object>) m;
            }
            // Else it is an error body -> PartnerApiException 502.
            throw new PartnerApiException("Delhivery B2C returned no rate: " + m, 502);
        }
        // Anything else -> PartnerApiException 502.
        throw new PartnerApiException("Delhivery B2C returned an empty or unknown response: " + response, 502);
    }

    /** Numbers or numeric strings; anything else (null, nested object, text) counts as 0. */
    // Any value -> BigDecimal.
    private static BigDecimal num(Object value) {
        // Number?
        if (value instanceof Number n) {
            // -> exact decimal.
            return new BigDecimal(n.toString());
        }
        // Non-blank text?
        if (value instanceof String s && !s.isBlank()) {
            // Try
            try {
                // parse.
                return new BigDecimal(s.trim());
            // Not a number?
            } catch (NumberFormatException ignored) {
                // -> 0.
                return BigDecimal.ZERO;
            }
        }
        // Anything else -> 0.
        return BigDecimal.ZERO;
    }
}
