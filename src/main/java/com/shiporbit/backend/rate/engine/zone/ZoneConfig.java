package com.shiporbit.backend.rate.engine.zone;

import com.shiporbit.backend.rate.engine.PincodeInfo;
import com.shiporbit.backend.rate.entity.PricingModel;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Zone rules for every partner and pricing model (SO_ZONE_GROUP, SO_ZONE_RULE, SO_ZONE_PAIR).
 * Immutable, so the cached instance is shared safely across requests.
 * Used only when SO_ZONE_MAP has no row for the exact pincode pair: SO_ZONE_MAP stays an override.
 */
// Immutable holder: partner -> pricing model -> rules.
public record ZoneConfig(Map<Integer, Map<PricingModel, PartnerZoneRules>> partners) {

    // Empty config.
    public static final ZoneConfig EMPTY = new ZoneConfig(Map.of());

    // Compact constructor: deep immutable copy.
    public ZoneConfig {
        // Working copy.
        Map<Integer, Map<PricingModel, PartnerZoneRules>> copy = new HashMap<>();
        // Copy each partner's model map.
        partners.forEach((partnerId, byModel) -> copy.put(partnerId, Map.copyOf(byModel)));
        // Freeze the outer map.
        partners = Map.copyOf(copy);
    }

    /** Pricing models that have zone rules for this partner. */
    // Models with zone rules for this partner (Step 3c loop).
    public Set<PricingModel> modelsFor(Integer partnerId) {
        // Empty set when the partner has none.
        return partners.getOrDefault(partnerId, Map.of()).keySet();
    }

    // Location key for origin -> destination under this partner + model.
    public Optional<String> resolve(Integer partnerId, PricingModel model, PincodeInfo origin, PincodeInfo destination) {
        // The partner's rules for this model (null = none).
        PartnerZoneRules rules = partners.getOrDefault(partnerId, Map.of()).get(model);
        // None -> empty; else evaluate the rules in order (PartnerZoneRules.resolve).
        return rules == null ? Optional.empty() : rules.resolve(origin, destination);
    }
}
