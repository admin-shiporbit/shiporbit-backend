package com.shiporbit.backend.rate.engine.rule;

import com.shiporbit.backend.rate.core.HyperlocalOptions;
import com.shiporbit.backend.rate.core.QuoteStatus;
import com.shiporbit.backend.rate.engine.PricingContext;
import com.shiporbit.backend.rate.engine.Rejection;

import java.math.BigDecimal;
import java.util.Optional;

/** HYPERLOCAL: needs distance and vehicle; distance must be within the card's MAX_DISTANCE_KM. */
// Hyperlocal model rule: distance and vehicle given and within limit.
public final class HyperlocalDistanceRule implements EligibilityRule {

    @Override
    // Check.
    public Optional<Rejection> check(PricingContext ctx) {
        // Hyperlocal options (NONE for /compare).
        HyperlocalOptions hl = ctx.request().hyperlocal();
        // No distance?
        if (hl.distanceKm() == null) {
            // -> DISTANCE_REQUIRED (what /compare gets for hyperlocal; then filtered out by mode).
            return Optional.of(new Rejection(QuoteStatus.DISTANCE_REQUIRED,
                    "hyperlocal.distanceKm is required for same-city services"));
        }
        // No vehicle?
        if (hl.vehicle() == null) {
            // -> VALIDATION_ERROR.
            return Optional.of(new Rejection(QuoteStatus.VALIDATION_ERROR,
                    "hyperlocal.vehicle is required for same-city services (e.g. BIKE)"));
        }
        // Card MAX_DISTANCE_KM.
        BigDecimal max = ctx.card().maxDistanceKm();
        // Set and exceeded?
        if (max != null && hl.distanceKm().compareTo(max) > 0) {
            // -> NOT_SERVICEABLE.
            return Optional.of(new Rejection(QuoteStatus.NOT_SERVICEABLE,
                    "Distance " + hl.distanceKm().stripTrailingZeros().toPlainString()
                            + " km exceeds max " + max.stripTrailingZeros().toPlainString() + " km"));
        }
        // Passed.
        return Optional.empty();
    }
}
