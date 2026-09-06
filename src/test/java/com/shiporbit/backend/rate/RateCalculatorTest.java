package com.shiporbit.backend.rate;

import com.shiporbit.backend.exception.PartnerNotServiceableException;
import com.shiporbit.backend.rate.dto.RateBreakdown;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateCalculatorTest {

    @Test
    void appliesGstOnTopOfTheStrategysPreTaxTotal() {
        RateCalculator calculator = new RateCalculator(List.of(fakeStrategy("FAKE", "Fake Partner", 1000.0, true)));

        RateBreakdown breakdown = calculator.calculate("FAKE", intraStateRequest());

        // The strategy only set `total`; GST fields must be RateCalculator's doing.
        assertEquals(1000.0, breakdown.total, 0.001);
        assertEquals(90.0, breakdown.cgst, 0.001);
        assertEquals(90.0, breakdown.sgst, 0.001);
        assertEquals(1180.0, breakdown.grandTotal, 0.001);
    }

    @Test
    void compareAllAlsoAppliesGstToEveryResult() {
        RateCalculator calculator = new RateCalculator(List.of(
                fakeStrategy("A", "Partner A", 1000.0, true),
                fakeStrategy("B", "Partner B", 2000.0, true)
        ));

        Map<String, RateBreakdown> results = calculator.calculateForAllServiceablePartners(intraStateRequest());

        assertEquals(1180.0, results.get("A").grandTotal, 0.001);
        assertEquals(2360.0, results.get("B").grandTotal, 0.001);
    }

    @Test
    void unknownPartnerCodeThrowsIllegalArgument() {
        RateCalculator calculator = new RateCalculator(List.of(fakeStrategy("A", "Partner A", 1000.0, true)));

        assertThrows(IllegalArgumentException.class, () -> calculator.calculate("UNKNOWN", intraStateRequest()));
    }

    @Test
    void notServiceablePartnerThrowsDedicatedException() {
        RateCalculator calculator = new RateCalculator(List.of(fakeStrategy("A", "Partner A", 1000.0, false)));

        assertThrows(PartnerNotServiceableException.class, () -> calculator.calculate("A", intraStateRequest()));
    }

    @Test
    void notServiceablePartnersAreExcludedFromCompareAll() {
        RateCalculator calculator = new RateCalculator(List.of(
                fakeStrategy("A", "Partner A", 1000.0, true),
                fakeStrategy("B", "Partner B", 2000.0, false)
        ));

        Map<String, RateBreakdown> results = calculator.calculateForAllServiceablePartners(intraStateRequest());

        assertTrue(results.containsKey("A"));
        assertFalse(results.containsKey("B"));
    }

    private ShipmentRequest intraStateRequest() {
        return ShipmentRequest.builder()
                .sourceZone(Zones.N1)
                .destZone(Zones.N1)
                .sourceStateOrCity("Delhi")
                .destStateOrCity("Delhi")
                .actualWeightKg(10)
                .declaredValue(100)
                .build();
    }

    private DeliveryRateStrategy fakeStrategy(String code, String name, double total, boolean serviceable) {
        return new DeliveryRateStrategy() {
            @Override
            public String getPartnerCode() {
                return code;
            }

            @Override
            public String getPartnerName() {
                return name;
            }

            @Override
            public RateBreakdown calculateRate(ShipmentRequest request) {
                RateBreakdown breakdown = new RateBreakdown();
                breakdown.total = total;
                return breakdown;
            }

            @Override
            public boolean isServiceable(ShipmentRequest request) {
                return serviceable;
            }
        };
    }
}
