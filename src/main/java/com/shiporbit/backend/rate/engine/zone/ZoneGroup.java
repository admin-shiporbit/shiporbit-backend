package com.shiporbit.backend.rate.engine.zone;

import com.shiporbit.backend.rate.engine.PincodeInfo;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A named grouping of pincodes for one partner + pricing model (SO_ZONE_GROUP rows with the same GROUP_NAME),
 * e.g. REGION: state UP -> NORTH, city MUMBAI -> W1.
 * A city match wins over a state match, so a city can sit in a different region from the rest of its state
 * (XpressBees: Noida is N1 although Uttar Pradesh is N2).
 */
public record ZoneGroup(String name, Map<String, String> byCity, Map<Integer, String> byState) {

    public ZoneGroup {
        byCity = Map.copyOf(byCity);        // keys are upper-case CITY_CODEs
        byState = Map.copyOf(byState);
    }

    /** The pincode's value in this group; empty when neither its city nor its state is listed. */
    public Optional<String> valueOf(PincodeInfo pincode) {
        if (pincode.cityCode() != null) {
            String byCityValue = byCity.get(pincode.cityCode().toUpperCase(Locale.ROOT));
            if (byCityValue != null) {
                return Optional.of(byCityValue);
            }
        }
        // Map.copyOf maps reject get(null), so guard a missing state.
        return pincode.stateId() == null ? Optional.empty() : Optional.ofNullable(byState.get(pincode.stateId()));
    }

    /** Every value this group can produce (used to check SO_ZONE_PAIR rows for typos). */
    public Set<String> values() {
        Set<String> values = new HashSet<>(byCity.values());
        values.addAll(byState.values());
        return values;
    }
}
