package com.shiporbit.backend.rate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ZonesTest {

    @Test
    void resolvesZoneForKnownState() {
        assertEquals(Zones.N1, Zones.fromState("Delhi"));
        assertEquals(Zones.S1, Zones.fromState("Karnataka"));
        assertEquals(Zones.C, Zones.fromState("Madhya Pradesh"));
    }

    @Test
    void isCaseInsensitiveAndTrims() {
        assertEquals(Zones.NE, Zones.fromState("  sikkim  "));
    }

    @Test
    void throwsForUnknownState() {
        assertThrows(IllegalArgumentException.class, () -> Zones.fromState("Narnia"));
    }

    @Test
    void throwsForBlankState() {
        assertThrows(IllegalArgumentException.class, () -> Zones.fromState(" "));
    }
}
