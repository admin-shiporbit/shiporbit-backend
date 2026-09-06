package com.shiporbit.backend.rate.delhivery;

import com.shiporbit.backend.rate.Zones;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DelhiveryRateSourceTest {

    private final DelhiveryRateSource rateSource = new DelhiveryRateSource();

    @Test
    void returnsZoneMatrixRateWhenNoOverrideApplies() {
        assertEquals(13.5, rateSource.getRate(Zones.N1, Zones.S1, "Warangal"), 0.0001);
        assertEquals(7.0, rateSource.getRate(Zones.N1, Zones.N1, null), 0.0001);
    }

    @Test
    void stateOverrideTakesPriorityOverZoneMatrix() {
        // Plain N1->NE zone-matrix rate is 19, but Sikkim has an override of 24.
        assertEquals(24.0, rateSource.getRate(Zones.N1, Zones.NE, "Sikkim"), 0.0001);
    }

    @Test
    void overrideLookupIsCaseInsensitive() {
        assertEquals(34.0, rateSource.getRate(Zones.N1, Zones.NE, "arunachal pradesh"), 0.0001);
    }

    @Test
    void throwsWhenNoRateIsConfigured() {
        // Matrix only has entries for source zones actually seeded (N1, N2, E, NE, W1, W2, S1, S2, C).
        assertThrows(IllegalArgumentException.class, () -> rateSource.getRate(null, Zones.S1, null));
    }
}
