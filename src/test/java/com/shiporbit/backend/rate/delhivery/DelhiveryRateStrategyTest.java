package com.shiporbit.backend.rate.delhivery;

import com.shiporbit.backend.dto.Dimension;
import com.shiporbit.backend.rate.RiskType;
import com.shiporbit.backend.rate.ShipmentRequest;
import com.shiporbit.backend.rate.Zones;
import com.shiporbit.backend.rate.dto.RateBreakdown;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelhiveryRateStrategyTest {

    private final DelhiveryRateStrategy strategy =
            new DelhiveryRateStrategy(new DelhiveryRateSource(), new DelhiveryRateCardConfig());

    @Test
    void reportsPartnerIdentity() {
        assertEquals("DELHIVERY", strategy.getPartnerCode());
        assertEquals("Delhivery", strategy.getPartnerName());
    }

    @Test
    void isServiceableByDefault() {
        assertTrue(strategy.isServiceable(minimalRequest().build()));
    }

    @Test
    void requiresSourceAndDestZone() {
        ShipmentRequest missingZones = ShipmentRequest.builder()
                .actualWeightKg(10)
                .declaredValue(100)
                .build();

        assertThrows(IllegalArgumentException.class, () -> strategy.calculateRate(missingZones));
    }

    @Test
    void calculatesFullBreakdownForVolumetricHeavyShipment() {
        // N1 -> S1, no override for "Warangal" so the plain zone-matrix rate (13.5) applies.
        // Volumetric weight (60*60*60/4500 = 48kg) exceeds both the 20kg actual weight and
        // the 20kg per-LR minimum, so 48kg is what everything downstream is based on.
        ShipmentRequest request = ShipmentRequest.builder()
                .sourceZone(Zones.N1)
                .destZone(Zones.S1)
                .destStateOrCity("Warangal")
                .actualWeightKg(20)
                .packages(List.of(new Dimension(60.0, 60.0, 60.0)))
                .declaredValue(1)
                .riskType(RiskType.CARRIER)
                .numberOfLRs(1)
                .build();

        RateBreakdown b = strategy.calculateRate(request);

        assertEquals(20.0, b.actualWeightKg, 0.001);
        assertEquals(48.0, b.volumetricWeightKg, 0.001);
        assertEquals(48.0, b.chargeableWeightKg, 0.001);
        assertEquals(13.5, b.ratePerKg, 0.001);
        assertEquals(648.0, b.baseFreight, 0.001);   // 48 * 13.5
        assertEquals(129.6, b.fsc, 0.001);            // 20% of baseFreight
        assertEquals(150.0, b.processing, 0.001);     // flat per LR
        assertEquals(200.0, b.rov, 0.001);            // carrier-risk floor (declared value too small to beat it)
        assertEquals(0.0, b.handling, 0.001);         // 48kg doesn't hit any handling slab (slabs start at 100kg)
        assertEquals(0.0, b.oda, 0.001);              // odaEnabled=false until we have the real exempt-pincode list
        assertEquals(0.0, b.floorDeliveryCharge, 0.001);
        assertEquals(0.0, b.mallDeliveryCharge, 0.001);
        assertEquals(100.0, b.greenTax, 0.001);        // floored at greenTaxMinPerLR
        assertEquals(1227.6, b.subtotal, 0.001);
        assertEquals(1228.0, b.total, 0.001);          // rounded up from 1227.6
    }

    @Test
    void odaIsZeroUntilEnabledWithRealExemptPincodeData() {
        // Even a "worst case" ODA shipment (destination pincode never in the - currently
        // empty - exempt set) should charge nothing while odaEnabled is false.
        ShipmentRequest request = ShipmentRequest.builder()
                .sourceZone(Zones.N1)
                .destZone(Zones.S1)
                .deliveryPincode("999999")
                .actualWeightKg(600)
                .declaredValue(1000)
                .build();

        RateBreakdown b = strategy.calculateRate(request);

        assertEquals(0.0, b.oda, 0.001);
    }

    @Test
    void stateOverrideChangesBaseRateAndDownstreamCharges() {
        // Sikkim overrides N1->NE to 24/kg instead of the zone-matrix 19/kg.
        ShipmentRequest request = ShipmentRequest.builder()
                .sourceZone(Zones.N1)
                .destZone(Zones.NE)
                .destStateOrCity("Sikkim")
                .actualWeightKg(25)
                .declaredValue(0)
                .build();

        RateBreakdown b = strategy.calculateRate(request);

        assertEquals(24.0, b.ratePerKg, 0.001);
    }

    @Test
    void conditionalChargesOnlyApplyWhenFlagged() {
        ShipmentRequest withMall = minimalRequest().mallDelivery(true).build();
        ShipmentRequest withoutMall = minimalRequest().build();

        RateBreakdown withMallBreakdown = strategy.calculateRate(withMall);
        RateBreakdown withoutMallBreakdown = strategy.calculateRate(withoutMall);

        assertTrue(withMallBreakdown.mallDeliveryCharge > 0);
        assertEquals(0.0, withoutMallBreakdown.mallDeliveryCharge, 0.001);
    }

    private ShipmentRequest.ShipmentRequestBuilder minimalRequest() {
        return ShipmentRequest.builder()
                .sourceZone(Zones.N1)
                .destZone(Zones.N1)
                .actualWeightKg(15)
                .declaredValue(500);
    }
}
