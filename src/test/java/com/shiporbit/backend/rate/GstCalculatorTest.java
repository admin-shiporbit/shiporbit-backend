package com.shiporbit.backend.rate;

import com.shiporbit.backend.rate.dto.RateBreakdown;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GstCalculatorTest {

    @Test
    void splitsCgstAndSgstForIntraStateShipment() {
        RateBreakdown breakdown = breakdownWithTotal(1000.0);
        ShipmentRequest request = ShipmentRequest.builder()
                .sourceStateOrCity("Delhi")
                .destStateOrCity("delhi") // case-insensitive match
                .build();

        GstCalculator.apply(breakdown, request);

        assertEquals(18.0, breakdown.gstRate, 0.001);
        assertEquals(90.0, breakdown.cgst, 0.001);
        assertEquals(90.0, breakdown.sgst, 0.001);
        assertEquals(0.0, breakdown.igst, 0.001);
        assertEquals(180.0, breakdown.gstAmount, 0.001);
        assertEquals(1180.0, breakdown.grandTotal, 0.001);
    }

    @Test
    void chargesIgstForInterStateShipment() {
        RateBreakdown breakdown = breakdownWithTotal(1000.0);
        ShipmentRequest request = ShipmentRequest.builder()
                .sourceStateOrCity("Delhi")
                .destStateOrCity("Karnataka")
                .build();

        GstCalculator.apply(breakdown, request);

        assertEquals(0.0, breakdown.cgst, 0.001);
        assertEquals(0.0, breakdown.sgst, 0.001);
        assertEquals(180.0, breakdown.igst, 0.001);
        assertEquals(180.0, breakdown.gstAmount, 0.001);
        assertEquals(1180.0, breakdown.grandTotal, 0.001);
    }

    @Test
    void defaultsToInterStateWhenEitherStateIsUnknown() {
        RateBreakdown breakdown = breakdownWithTotal(1000.0);
        ShipmentRequest request = ShipmentRequest.builder()
                .sourceStateOrCity(null)
                .destStateOrCity("Karnataka")
                .build();

        GstCalculator.apply(breakdown, request);

        assertEquals(0.0, breakdown.cgst, 0.001);
        assertEquals(180.0, breakdown.igst, 0.001);
    }

    private RateBreakdown breakdownWithTotal(double total) {
        RateBreakdown breakdown = new RateBreakdown();
        breakdown.total = total;
        return breakdown;
    }
}
