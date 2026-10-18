package com.shiporbit.backend.rate.routing;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartnerAllowlistPropertiesTest {

    @Test
    void enabledAreQuotedDisabledAndUnlistedAreNot() {
        PartnerAllowlistProperties p = new PartnerAllowlistProperties(List.of("AMAZON_SHIPPING", "DTDC"), List.of("DELHIVERY"));
        assertTrue(p.allows("AMAZON_SHIPPING"));
        assertTrue(p.allows("dtdc"));
        assertEquals(false, p.allows("DELHIVERY"));       // disabled
        assertEquals(false, p.allows("EKART"));           // in neither list
        assertEquals(false, p.allows(null));
    }

    @Test
    void disabledWinsWhenInBothLists() {
        PartnerAllowlistProperties p = new PartnerAllowlistProperties(List.of("TCI", "EKART"), List.of("tci"));
        assertEquals(false, p.allows("TCI"));
        assertEquals(List.of("TCI"), p.inBoth());
        assertEquals(List.of("EKART"), p.names(true));
        assertEquals(List.of("TCI"), p.names(false));
    }

    @Test
    void missingEnabledKeyQuotesEveryoneNotDisabled() {
        PartnerAllowlistProperties p = new PartnerAllowlistProperties(null, List.of("DELHIVERY"));
        assertEquals(false, p.configured());
        assertTrue(p.allows("EKART"));
        assertEquals(false, p.allows("DELHIVERY"));
        assertTrue(new PartnerAllowlistProperties(null, null).allows("DELHIVERY"));
    }

    @Test
    void emptyListsAndBlanks() {
        PartnerAllowlistProperties p = new PartnerAllowlistProperties(Arrays.asList(" ekart ", "", null, "EKART"), List.of());
        assertEquals(List.of("EKART"), p.enabled());
        assertEquals(false, new PartnerAllowlistProperties(List.of(), List.of()).allows("EKART"));   // enabled: [] = none
        assertTrue(PartnerAllowlistProperties.sameName("shree-maruti", "SHREE_MARUTI"));
    }
}
