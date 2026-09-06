package com.shiporbit.backend.rate.delhivery;

import com.shiporbit.backend.rate.RateSource;
import com.shiporbit.backend.rate.Zones;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Delhivery's zone-matrix base rate, with per state/city overrides for remote
 * destinations (e.g. Arunachal Pradesh, Ladakh) that take priority over the plain
 * zone-matrix rate. In production, back this with a DB table instead of hardcoding
 * the ~80 rows (9 zones x 9 zones + overrides) from the rate card.
 */
@Component
public class DelhiveryRateSource implements RateSource {

    private final Map<Zones, Map<Zones, Double>> zoneMatrix = new EnumMap<>(Zones.class);
    // destination state/city (lower-cased) -> source zone -> override rate
    private final Map<String, Map<Zones, Double>> overrides = new HashMap<>();

    public DelhiveryRateSource() {
        seedZoneMatrix();
        seedOverrides();
    }

    @Override
    public double getRate(Zones sourceZone, Zones destZone, String destStateOrCity) {
        if (destStateOrCity != null) {
            Map<Zones, Double> byZone = overrides.get(destStateOrCity.toLowerCase(Locale.ROOT));
            if (byZone != null && byZone.containsKey(sourceZone)) {
                return byZone.get(sourceZone);
            }
        }
        Map<Zones, Double> row = zoneMatrix.get(sourceZone);
        if (row == null || !row.containsKey(destZone)) {
            throw new IllegalArgumentException("No Delhivery rate found for " + sourceZone + " -> " + destZone);
        }
        return row.get(destZone);
    }

    private void setMatrixRate(Zones source, Zones dest, double rate) {
        zoneMatrix.computeIfAbsent(source, z -> new EnumMap<>(Zones.class)).put(dest, rate);
    }

    private void setOverrideRate(String destStateOrCity, Zones sourceZone, double rate) {
        overrides.computeIfAbsent(destStateOrCity.toLowerCase(Locale.ROOT), k -> new EnumMap<>(Zones.class))
                .put(sourceZone, rate);
    }

    private void seedZoneMatrix() {
        Zones[] z = {Zones.N1, Zones.N2, Zones.E, Zones.NE, Zones.W1, Zones.W2, Zones.S1, Zones.S2, Zones.C};
        double[][] matrix = {
                {7,    7,    12.5, 19,   9.5,  10.5, 13.5, 17,   9.5},  // N1
                {7,    7,    13,   20.5, 10.5, 10.5, 15.5, 18,   10.5}, // N2
                {10.5, 12.5, 7,    11.5, 10.5, 12.5, 10.5, 13.5, 9.5},  // E
                {8.5,  12.5, 9,    7,    11,   12.5, 12.5, 17,   10.5}, // NE
                {9.5,  10.5, 15.5, 24,   7,    7,    11.5, 14,   9.5},  // W1
                {10.5, 11,   13.5, 21.5, 7,    7,    9.5,  12.5, 9.5},  // W2
                {10.5, 12.5, 12.5, 18.5, 10.5, 9.5,  7,    9.5,  9.5},  // S1
                {12.5, 12.5, 12.5, 23,   10.5, 10.5, 7,    7,    9.5},  // S2
                {9.5,  10.5, 12.5, 18.5, 7,    9.5,  11,   13.5, 7}     // C
        };
        for (int i = 0; i < z.length; i++) {
            for (int j = 0; j < z.length; j++) {
                setMatrixRate(z[i], z[j], matrix[i][j]);
            }
        }
    }

    private void seedOverrides() {
        setOverrideRate("Arunachal Pradesh", Zones.N1, 34);
        setOverrideRate("Arunachal Pradesh", Zones.E, 26.5);
        setOverrideRate("Sikkim", Zones.N1, 24);
        setOverrideRate("Sikkim", Zones.NE, 12);
        setOverrideRate("Ladakh", Zones.N1, 57);
        setOverrideRate("Guwahati-assam", Zones.NE, 7);
        // TODO: load the remaining override rows (see "Source/Destination" table in
        // the rate card sheet) from the spreadsheet into a DB table in production.
    }
}
