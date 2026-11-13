package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.error.ErrorCode;
import com.shiporbit.backend.exception.PartnerApiException;
import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.dto.response.RateResponse;
import com.shiporbit.backend.rate.serviceability.Serviceability;
import com.shiporbit.backend.rate.serviceability.ServiceabilityJson;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ekart Elite request/response mapping. Pure (no Spring, no network) so it is unit-tested.
 * Shapes are from Ekart's published OpenAPI (app.elite.ekartlogistics.in/api/docs); they are NOT yet checked
 * against a real response - replace the test samples with real ones after the first call.
 */
final class EkartResponseParser {

    /** Allowed gap (Rs) when deciding whether "total" already includes "taxes". */
    private static final BigDecimal TOLERANCE = BigDecimal.ONE;

    private EkartResponseParser() {
    }

    // ---------------------------------------------------------------- serviceability

    /**
     * GET /api/v2/serviceability/{pincode} -> {status, pincode, remark, details{cod, max_cod_amount, forward_pickup,
     * forward_drop, reverse_pickup, reverse_drop, city, state}}.
     * status true -> SERVICEABLE with reason "pickup=Y,drop=Y,cod=Y,max_cod=25000"; false -> NOT_SERVICEABLE;
     * no status -> UNKNOWN.
     */
    static Serviceability pin(Object response, String pin) {
        Map<String, Object> root = ServiceabilityJson.asMap(response);
        Object status = root.get("status");
        if (!(status instanceof Boolean ok)) {
            return Serviceability.unknown(ErrorCode.SERVICEABILITY_UNCONFIRMED, "Ekart serviceability: no status in response");
        }
        if (!ok) {
            Object remark = root.get("remark");
            return Serviceability.no(ErrorCode.NOT_SERVICEABLE, "Ekart does not serve pincode " + pin
                    + (remark == null || remark.toString().isBlank() ? "" : " (" + remark + ")"));
        }
        Map<String, Object> d = ServiceabilityJson.asMap(root.get("details"));
        return Serviceability.yes("pickup=" + yn(d.get("forward_pickup")) + ",drop=" + yn(d.get("forward_drop"))
                + ",cod=" + yn(d.get("cod")) + ",max_cod=" + number(d.get("max_cod_amount")).toPlainString());
    }

    /** Origin needs forward pickup; destination needs forward drop, and for COD: cod + amount within max_cod. */
    static Serviceability evaluate(Serviceability s, String pin, boolean origin, boolean cod, BigDecimal codAmount) {
        if (s.status() != Serviceability.Status.SERVICEABLE) {
            return s;
        }
        if (origin) {
            return DelhiveryPinParser.flag(s.reason(), "pickup")
                    ? Serviceability.yes("Ekart origin " + pin + " pickup OK")
                    : Serviceability.no(ErrorCode.PICKUP_NOT_AVAILABLE, "Ekart: no pickup at origin " + pin);
        }
        if (!DelhiveryPinParser.flag(s.reason(), "drop")) {
            return Serviceability.no(ErrorCode.NOT_SERVICEABLE, "Ekart: no delivery at destination " + pin);
        }
        if (!cod) {
            return Serviceability.yes("Ekart destination " + pin + " drop OK");
        }
        if (!DelhiveryPinParser.flag(s.reason(), "cod")) {
            return Serviceability.no(ErrorCode.COD_NOT_SERVICEABLE, "Ekart: COD not available at destination " + pin);
        }
        BigDecimal maxCod = maxCod(s.reason());
        if (maxCod.signum() > 0 && codAmount != null && codAmount.compareTo(maxCod) > 0) {
            return Serviceability.no(ErrorCode.COD_NOT_SERVICEABLE,
                    "Ekart: COD amount " + codAmount.toPlainString() + " above limit " + maxCod.toPlainString() + " at " + pin);
        }
        return Serviceability.yes("Ekart destination " + pin + " COD OK");
    }

    static BigDecimal maxCod(String reason) {
        if (reason != null) {
            for (String part : reason.split(",")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2 && kv[0].trim().equals("max_cod")) {
                    return number(kv[1]);
                }
            }
        }
        return BigDecimal.ZERO;
    }

    // ---------------------------------------------------------------- estimate request

    /**
     * POST /data/pricing/estimate body. Weight in grams and dimensions in whole cm (Ekart's other APIs document
     * grams / cm; the estimate schema only says "integer" - confirm with the first real call).
     */
    static Map<String, Object> estimateBody(RequestParamRecord r, String billingClientType, String shippingDirection) {
        Map<String, Object> o = r.partnerOptions();
        boolean cod = Boolean.TRUE.equals(o.get("cod"));
        double codAmount = o.get("codAmount") instanceof Number n ? n.doubleValue() : 0.0;
        String mode = String.valueOf(o.getOrDefault("deliveryMode", "SURFACE")).toUpperCase();

        Map<String, Object> body = new LinkedHashMap<>();
        if (billingClientType != null && !billingClientType.isBlank()) {
            body.put("billingClientType", billingClientType.trim());
        }
        body.put("shippingDirection", shippingDirection);
        body.put("serviceType", "EXPRESS".equals(mode) || "AIR".equals(mode) ? "EXPRESS" : "SURFACE");
        body.put("pickupPincode", Integer.parseInt(r.sourcePinCode()));
        body.put("dropPincode", Integer.parseInt(r.destinationPinCode()));
        body.put("invoiceAmount", r.inventoryAmout());
        body.put("codAmount", cod ? codAmount : 0.0);
        body.put("weight", wholeUp(r.weight()));
        body.put("length", wholeUp(r.dimension() == null ? null : r.dimension().getLength()));
        body.put("width", wholeUp(r.dimension() == null ? null : r.dimension().getWidth()));
        body.put("height", wholeUp(r.dimension() == null ? null : r.dimension().getHeight()));
        return body;
    }

    /** Round up to a whole number, minimum 1 (Ekart rejects 0). */
    static int wholeUp(Double v) {
        return v == null || v <= 0 ? 1 : (int) Math.max(1, Math.ceil(v));
    }

    // ---------------------------------------------------------------- estimate response

    /**
     * Estimate answer (numbers as strings) -> RateResponse. finalFreight must include GST:
     * if total = parts + taxes it already does; if total = parts only, taxes are added. Anything else is kept as
     * Ekart's total (the difference shows as an ADJUSTMENT line).
     */
    static RateResponse estimate(Object response, String code, String name) {
        Map<String, Object> m = ServiceabilityJson.asMap(response);
        if (!m.containsKey("total")) {
            throw new PartnerApiException(ErrorCode.PARTNER_BAD_RESPONSE, "Ekart estimate: no total in response", null,
                    HttpStatus.BAD_GATEWAY.value());
        }
        BigDecimal shipping = number(m.get("shippingCharge"));
        BigDecimal fuel = number(m.get("fuelSurcharge"));
        BigDecimal codCharge = number(m.get("codCharge"));
        BigDecimal qc = number(m.get("qcCharge"));
        BigDecimal taxes = number(m.get("taxes"));
        BigDecimal total = number(m.get("total"));
        BigDecimal preTax = shipping.add(fuel).add(codCharge).add(qc);

        BigDecimal finalFreight = total;
        if (total.subtract(preTax).abs().compareTo(TOLERANCE) <= 0 && taxes.signum() > 0) {
            finalFreight = total.add(taxes);              // total was pre-tax
        }
        return new RateResponse(code, name, shipping, BigDecimal.ZERO, fuel, BigDecimal.ZERO, BigDecimal.ZERO,
                codCharge.add(qc), taxes, finalFreight.setScale(2, RoundingMode.HALF_UP));
    }

    // ---------------------------------------------------------------- helpers

    private static String yn(Object v) {
        return ServiceabilityJson.isYes(v) ? "Y" : "N";
    }

    static BigDecimal number(Object v) {
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        if (v != null) {
            String s = v.toString().replace(",", "").trim();
            if (!s.isEmpty()) {
                try {
                    return new BigDecimal(s);
                } catch (NumberFormatException ignored) {
                    // not a number -> 0
                }
            }
        }
        return BigDecimal.ZERO;
    }
}
