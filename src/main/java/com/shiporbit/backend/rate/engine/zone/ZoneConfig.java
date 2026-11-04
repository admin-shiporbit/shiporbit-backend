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
public record ZoneConfig(Map<Integer, Map<PricingModel, PartnerZoneRules>> partners) {

    public static final ZoneConfig EMPTY = new ZoneConfig(Map.of());

    public ZoneConfig {
        Map<Integer, Map<PricingModel, PartnerZoneRules>> copy = new HashMap<>();
        partners.forEach((partnerId, byModel) -> copy.put(partnerId, Map.copyOf(byModel)));
        partners = Map.copyOf(copy);
    }

    /** Pricing models that have zone rules for this partner. */
    public Set<PricingModel> modelsFor(Integer partnerId) {
        return partners.getOrDefault(partnerId, Map.of()).keySet();
    }

    public Optional<String> resolve(Integer partnerId, PricingModel model, PincodeInfo origin, PincodeInfo destination) {
        PartnerZoneRules rules = partners.getOrDefault(partnerId, Map.of()).get(model);
        return rules == null ? Optional.empty() : rules.resolve(origin, destination);
    }
}
