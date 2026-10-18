package com.shiporbit.backend.rate.serviceability;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceabilityParsingTest {

    @Test
    void findsFlagAnywhereAndReadsYN() {
        assertEquals(Optional.of(true), ServiceabilityJson.findFlag(Map.of("data", Map.of("serviceable", true))));
        assertEquals(Optional.of(false), ServiceabilityJson.findFlag(Map.of("isServiceable", "N")));
        assertEquals(Optional.of(true), ServiceabilityJson.findFlag(Map.of("data", List.of(
                Map.of("carrier", "A", "serviceable", false), Map.of("carrier", "SMILE", "serviceable", true)))));
        assertEquals(Optional.empty(), ServiceabilityJson.findFlag(Map.of("status", "ok")));
    }

    @Test
    void bothEndsMustPass() {
        var yes = Serviceability.yes("o");
        var no = Serviceability.no("dest not served");
        var unk = Serviceability.unknown("api down");
        assertEquals(Serviceability.Status.SERVICEABLE, Serviceability.both(yes, Serviceability.yes("d")).status());
        assertEquals(no, Serviceability.both(yes, no));
        assertEquals(no, Serviceability.both(unk, no));       // a definite NO beats UNKNOWN
        assertEquals(unk, Serviceability.both(yes, unk));
    }
}
