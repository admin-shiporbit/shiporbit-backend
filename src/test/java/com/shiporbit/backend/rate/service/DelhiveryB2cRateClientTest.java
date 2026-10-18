package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.dto.Dimension;
import com.shiporbit.backend.exception.PartnerApiException;
import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.dto.response.RateResponse;
import com.shiporbit.backend.rate.routing.DelhiveryB2cConfigProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelhiveryB2cRateClientTest {

    private static RequestParamRecord req(double grams, String paymentMode, double l, double w, double h) {
        return new RequestParamRecord(grams, "110110", "110084", new Dimension(h, w, l, 1), false,
                paymentMode, 1500.0, "fod", false, Map.of("codAmount", 1500.0, "deliveryMode", "SURFACE"));
    }

    @Test
    void below20kgIsB2cAt20kgAndAboveIsLtl() {
        assertTrue(DelhiveryB2cRateClient.isB2c(19_999.0, 20));
        assertFalse(DelhiveryB2cRateClient.isB2c(20_000.0, 20));
        assertFalse(DelhiveryB2cRateClient.isB2c(25_000.0, 20));
        assertFalse(DelhiveryB2cRateClient.isB2c(null, 20));
    }

    @Test
    void chargeableIsMaxOfDeadAndVolumetric() {
        assertEquals(2000, DelhiveryB2cRateClient.chargeableGrams(req(2000, "cod", 10, 10, 10), 5000));   // vol 200 g
        assertEquals(4800, DelhiveryB2cRateClient.chargeableGrams(req(2000, "cod", 40, 30, 20), 5000));   // vol 4800 g
    }

    @Test
    void queryMatchesDelhiveryCurl() {
        var props = new DelhiveryB2cConfigProperties("https://staging-express.delhivery.com", null, "x", null, null, null, null, null);
        var client = new DelhiveryB2cRateClient(null, props, null);
        Map<String, Object> q = client.buildQuery(req(2000, "cod", 10, 10, 10));
        assertEquals("S", q.get("md"));
        assertEquals("Delivered", q.get("ss"));
        assertEquals("110110", q.get("o_pin"));
        assertEquals("110084", q.get("d_pin"));
        assertEquals(2000L, q.get("cgm"));
        assertEquals("COD", q.get("pt"));
        assertEquals(1500.0, q.get("cod"));

        Map<String, Object> prepaid = client.buildQuery(req(2000, "prepaid", 10, 10, 10));
        assertEquals("Pre-paid", prepaid.get("pt"));
        assertEquals(0, prepaid.get("cod"));
    }

    @Test
    void mapsArrayResponse() {
        Object response = List.of(Map.of(
                "charge_DL", 60, "charge_FSC", 6.0, "charge_COD", 35, "charge_RTO", 0,
                "gross_amount", 101.0, "total_amount", 119.18,
                "tax_data", Map.of("IGST", 0, "CGST", 9.09, "SGST", 9.09),
                "zone", "A"));
        RateResponse r = DelhiveryB2cResponseMapper.map(response, "delhivery", "Delhivery");
        assertEquals(0, new BigDecimal("60").compareTo(r.baseFreight()));
        assertEquals(0, new BigDecimal("6.0").compareTo(r.surcharge()));
        assertEquals(0, new BigDecimal("35").compareTo(r.handlingCharges()));
        assertEquals(0, new BigDecimal("18.18").compareTo(r.gst()));
        assertEquals(0, new BigDecimal("119.18").compareTo(r.finalFreight()));
    }

    @Test
    void errorObjectFails() {
        assertThrows(PartnerApiException.class,
                () -> DelhiveryB2cResponseMapper.map(Map.of("error", "Invalid token"), "delhivery", "Delhivery"));
        assertThrows(PartnerApiException.class,
                () -> DelhiveryB2cResponseMapper.map(List.of(), "delhivery", "Delhivery"));
    }
}
