package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.dto.Dimension;
import com.shiporbit.backend.exception.PartnerApiException;
import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.dto.response.RateResponse;
import com.shiporbit.backend.rate.serviceability.Serviceability;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Samples follow Ekart's published OpenAPI; replace with real responses after the first live call. */
class EkartResponseParserTest {

    private static Object pinResponse(boolean status, boolean pickup, boolean drop, boolean cod, int maxCod) {
        Map<String, Object> details = Map.of("cod", cod, "max_cod_amount", maxCod, "forward_pickup", pickup,
                "forward_drop", drop, "reverse_pickup", true, "reverse_drop", true, "city", "Bengaluru", "state", "KA");
        Map<String, Object> root = new HashMap<>(Map.of("status", status, "pincode", 560008, "remark", status ? "" : "Not serviceable"));
        root.put("details", details);
        return root;
    }

    @Test
    void servedPincodeCarriesFlags() {
        Serviceability s = EkartResponseParser.pin(pinResponse(true, true, true, true, 25000), "560008");
        assertEquals(Serviceability.Status.SERVICEABLE, s.status());
        assertEquals(new BigDecimal("25000"), EkartResponseParser.maxCod(s.reason()));
    }

    @Test
    void statusFalseIsNotServiceableAndMissingStatusIsUnknown() {
        assertEquals(Serviceability.Status.NOT_SERVICEABLE, EkartResponseParser.pin(pinResponse(false, false, false, false, 0), "560008").status());
        assertEquals(Serviceability.Status.UNKNOWN, EkartResponseParser.pin(Map.of("pincode", 560008), "560008").status());
    }

    @Test
    void originNeedsPickupDestinationNeedsDropAndCodWithinLimit() {
        Serviceability noPickup = EkartResponseParser.pin(pinResponse(true, false, true, true, 25000), "560008");
        assertEquals(Serviceability.Status.NOT_SERVICEABLE, EkartResponseParser.evaluate(noPickup, "560008", true, false, BigDecimal.ZERO).status());

        Serviceability served = EkartResponseParser.pin(pinResponse(true, true, true, true, 25000), "560008");
        assertEquals(Serviceability.Status.SERVICEABLE, EkartResponseParser.evaluate(served, "560008", false, true, new BigDecimal("5000")).status());
        assertEquals(Serviceability.Status.NOT_SERVICEABLE, EkartResponseParser.evaluate(served, "560008", false, true, new BigDecimal("30000")).status());

        Serviceability noCod = EkartResponseParser.pin(pinResponse(true, true, true, false, 0), "560008");
        assertEquals(Serviceability.Status.SERVICEABLE, EkartResponseParser.evaluate(noCod, "560008", false, false, BigDecimal.ZERO).status());
        assertEquals(Serviceability.Status.NOT_SERVICEABLE, EkartResponseParser.evaluate(noCod, "560008", false, true, new BigDecimal("500")).status());
    }

    @Test
    void estimateBodyUsesGramsWholeCmAndCodAmount() {
        Dimension d = new Dimension(10.2, 20.0, 30.0, 1);   // height, width, length
        RequestParamRecord r = new RequestParamRecord(1500.4, "400001", "560001", d, false, null, 5000.0, null, false,
                Map.of("cod", true, "deliveryMode", "SURFACE", "insuranceAmount", 5000.0, "codAmount", 5000.0));
        Map<String, Object> body = EkartResponseParser.estimateBody(r, "", "FORWARD");
        assertFalse(body.containsKey("billingClientType"));
        assertEquals("FORWARD", body.get("shippingDirection"));
        assertEquals("SURFACE", body.get("serviceType"));
        assertEquals(400001, body.get("pickupPincode"));
        assertEquals(1501, body.get("weight"));
        assertEquals(11, body.get("height"));
        assertEquals(5000.0, body.get("codAmount"));
        assertEquals(5000.0, body.get("invoiceAmount"));
    }

    @Test
    void estimateTotalIncludingTaxesIsKept() {
        RateResponse r = EkartResponseParser.estimate(Map.of("shippingCharge", "100.00", "fuelSurcharge", "10.00",
                "codCharge", "30.00", "qcCharge", "0", "taxes", "25.20", "total", "165.20"), "ekart", "Ekart");
        assertEquals(new BigDecimal("165.20"), r.finalFreight());
        assertEquals(new BigDecimal("25.20"), r.gst());
        assertEquals(new BigDecimal("30.00"), r.handlingCharges());
    }

    @Test
    void estimateTotalWithoutTaxesGetsTaxesAdded() {
        RateResponse r = EkartResponseParser.estimate(Map.of("shippingCharge", "100", "fuelSurcharge", "10",
                "codCharge", "0", "qcCharge", "0", "taxes", "19.80", "total", "110"), "ekart", "Ekart");
        assertEquals(new BigDecimal("129.80"), r.finalFreight());
    }

    @Test
    void estimateWithoutTotalFails() {
        assertThrows(PartnerApiException.class, () -> EkartResponseParser.estimate(Map.of("shippingCharge", "100"), "ekart", "Ekart"));
    }
}
