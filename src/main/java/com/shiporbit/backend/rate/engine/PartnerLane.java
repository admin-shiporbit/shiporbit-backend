package com.shiporbit.backend.rate.engine;

import com.shiporbit.backend.rate.entity.PricingModel;

import java.util.Map;

/**
 * One partner's view of the requested lane.
 * origin/destination null = partner has no row for that pincode (not serviceable).
 * locationKeys: SO_ZONE_MAP.LOCATION_KEY per pricing model for this origin -> destination.
 */
public record PartnerLane(PincodeInfo origin, PincodeInfo destination, Map<PricingModel, String> locationKeys) {

    public static final PartnerLane EMPTY = new PartnerLane(null, null, Map.of());

    public PartnerLane {
        locationKeys = Map.copyOf(locationKeys);
    }
}
