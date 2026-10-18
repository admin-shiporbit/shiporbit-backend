package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.rate.serviceability.Serviceability;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelhiveryPinParserTest {

    private static Object pin(String pickup, String cod, String prepaid, String remarks) {
        return Map.of("delivery_codes", List.of(Map.of("postal_code", Map.of(
                "pin", 560001, "pickup", pickup, "cod", cod, "pre_paid", prepaid, "is_oda", "N", "remarks", remarks))));
    }

    @Test
    void servedPinCarriesFlags() {
        Serviceability s = DelhiveryPinParser.parse(pin("Y", "N", "Y", ""), "560001");
        assertEquals(Serviceability.Status.SERVICEABLE, s.status());
        assertTrue(DelhiveryPinParser.flag(s.reason(), "pickup"));
        assertFalse(DelhiveryPinParser.flag(s.reason(), "cod"));
        assertTrue(DelhiveryPinParser.flag(s.reason(), "pre_paid"));
    }

    @Test
    void emptyListIsNotServiceableAndEmbargoFails() {
        assertEquals(Serviceability.Status.NOT_SERVICEABLE,
                DelhiveryPinParser.parse(Map.of("delivery_codes", List.of()), "194103").status());
        assertEquals(Serviceability.Status.NOT_SERVICEABLE,
                DelhiveryPinParser.parse(pin("Y", "Y", "Y", "Embargo"), "560001").status());
        assertEquals(Serviceability.Status.UNKNOWN,
                DelhiveryPinParser.parse(Map.of("detail", "Unauthorized"), "560001").status());
    }
}
