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
final class DelhiveryB2cResponseMapper {

    private static final Set<String> FUEL_KEYS = Set.of("charge_FSC", "charge_FS");

    private DelhiveryB2cResponseMapper() {
    }

    static RateResponse map(Object response, String partnerCode, String partnerName) {
        Map<String, Object> row = firstRow(response);
        if (row.get("total_amount") == null) {
            throw new PartnerApiException("Delhivery B2C response has no total_amount: " + row, 502);
        }
        BigDecimal total = num(row.get("total_amount"));

        BigDecimal base = num(row.get("charge_DL"));
        BigDecimal fuel = FUEL_KEYS.stream().filter(row::containsKey).findFirst()
                .map(k -> num(row.get(k))).orElse(BigDecimal.ZERO);
        BigDecimal handling = BigDecimal.ZERO;
        for (Map.Entry<String, Object> e : row.entrySet()) {
            String key = e.getKey();
            if (key.startsWith("charge_") && !"charge_DL".equals(key) && !FUEL_KEYS.contains(key)) {
                handling = handling.add(num(e.getValue()));
            }
        }

        BigDecimal gst;
        if (row.get("tax_data") instanceof Map<?, ?> tax && !tax.isEmpty()) {
            gst = tax.values().stream().map(DelhiveryB2cResponseMapper::num).reduce(BigDecimal.ZERO, BigDecimal::add);
        } else if (row.get("gross_amount") != null) {
            gst = total.subtract(num(row.get("gross_amount")));
        } else {
            gst = BigDecimal.ZERO;
        }

        return new RateResponse(partnerCode, partnerName, base, BigDecimal.ZERO, fuel, BigDecimal.ZERO,
                BigDecimal.ZERO, handling, gst, total);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstRow(Object response) {
        if (response instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        if (response instanceof Map<?, ?> m) {
            // Error bodies come back as an object, e.g. {"error": "..."} / {"detail": "..."}
            if (m.get("total_amount") != null) {
                return (Map<String, Object>) m;
            }
            throw new PartnerApiException("Delhivery B2C returned no rate: " + m, 502);
        }
        throw new PartnerApiException("Delhivery B2C returned an empty or unknown response: " + response, 502);
    }

    /** Numbers or numeric strings; anything else (null, nested object, text) counts as 0. */
    private static BigDecimal num(Object value) {
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return new BigDecimal(s.trim());
            } catch (NumberFormatException ignored) {
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }
}
