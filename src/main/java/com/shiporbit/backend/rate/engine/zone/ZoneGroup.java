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
// One named group: city code -> value, state id -> value.
public record ZoneGroup(String name, Map<String, String> byCity, Map<Integer, String> byState) {

    // Compact constructor:
    public ZoneGroup {
        // freeze cities,
        byCity = Map.copyOf(byCity);        // keys are upper-case CITY_CODEs
        // freeze states.
        byState = Map.copyOf(byState);
    }

    /** The pincode's value in this group; empty when neither its city nor its state is listed. */
    // The pincode's value in this group.
    public Optional<String> valueOf(PincodeInfo pincode) {
        // Has a city code?
        if (pincode.cityCode() != null) {
            // Look it up (upper case), e.g. MUMBAI -> W1.
            String byCityValue = byCity.get(pincode.cityCode().toUpperCase(Locale.ROOT));
            // Found?
            if (byCityValue != null) {
                // City wins over state.
                return Optional.of(byCityValue);
            }
        }
        // Map.copyOf maps reject get(null), so guard a missing state.
        // Else by state id, e.g. 32 -> N1; no state -> empty.
        return pincode.stateId() == null ? Optional.empty() : Optional.ofNullable(byState.get(pincode.stateId()));
    }

    /** Every value this group can produce (used to check SO_ZONE_PAIR rows for typos). */
    // All values (used by problems()).
    public Set<String> values() {
        // City values
        Set<String> values = new HashSet<>(byCity.values());
        // + state values
        values.addAll(byState.values());
        // returned.
        return values;
    }
}
