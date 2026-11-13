package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.rate.serviceability.Serviceability;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shapes from real Delhivery LTL responses (log 2026-11-09). */
class DelhiveryLtlPinParserTest {

    private static Object served(Object pincode, Object paymentType, boolean oda) {
        return Map.of("success", true, "data", Map.of("b2b_fail_on_demand_pincodes", List.of(),
                "pincode_serviceability_data", List.of(Map.of("pincode", pincode, "payment_type", paymentType, "oda", oda))));
    }

    @Test
    void servedPinWithPaymentTypesAsJsonString() {
        Serviceability s = DelhiveryLtlPinParser.parse(
                served(201306, "[\"COD\", \"Pre-paid\", \"Cash\", \"REPL\", \"Pickup\"]", false), "201306");
        assertEquals(Serviceability.Status.SERVICEABLE, s.status());
        assertTrue(DelhiveryPinParser.flag(s.reason(), "pickup"));
        assertTrue(DelhiveryPinParser.flag(s.reason(), "cod"));
        assertTrue(DelhiveryPinParser.flag(s.reason(), "pre_paid"));
        assertFalse(DelhiveryPinParser.flag(s.reason(), "is_oda"));
    }

    @Test
    void servedPinWithPaymentTypesAsListAndNoPickup() {
        Serviceability s = DelhiveryLtlPinParser.parse(served("306001", List.of("Pre-paid"), true), "306001");
        assertEquals(Serviceability.Status.SERVICEABLE, s.status());
        assertFalse(DelhiveryPinParser.flag(s.reason(), "pickup"));
        assertFalse(DelhiveryPinParser.flag(s.reason(), "cod"));
        assertTrue(DelhiveryPinParser.flag(s.reason(), "pre_paid"));
        assertTrue(DelhiveryPinParser.flag(s.reason(), "is_oda"));
    }

    @Test
    void http400NotServiceableIsADefiniteNo() {
        Serviceability s = DelhiveryLtlPinParser.fromHttpError(400,
                "{\"success\":false,\"error\":{\"code\":400,\"message\":\"pin: 403519 is not serviceable.\"}}", "403519");
        assertEquals(Serviceability.Status.NOT_SERVICEABLE, s.status());
        assertTrue(s.cacheable());
        assertNull(DelhiveryLtlPinParser.fromHttpError(400, "{\"message\":\"weight is invalid\"}", "403519"));
        assertNull(DelhiveryLtlPinParser.fromHttpError(401, "not serviceable", "403519"));
    }

    @Test
    void unknownShapesStayUnknown() {
        assertEquals(Serviceability.Status.UNKNOWN, DelhiveryLtlPinParser.parse(Map.of("success", true), "201306").status());
        assertEquals(Serviceability.Status.UNKNOWN,
                DelhiveryLtlPinParser.parse(served(110001, "[\"Pickup\"]", false), "201306").status());
    }
}
