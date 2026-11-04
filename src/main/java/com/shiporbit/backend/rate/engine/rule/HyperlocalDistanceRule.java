package com.shiporbit.backend.rate.engine.rule;

import com.shiporbit.backend.rate.core.HyperlocalOptions;
import com.shiporbit.backend.rate.core.QuoteStatus;
import com.shiporbit.backend.rate.engine.PricingContext;
import com.shiporbit.backend.rate.engine.Rejection;

import java.math.BigDecimal;
import java.util.Optional;

/** HYPERLOCAL: needs distance and vehicle; distance must be within the card's MAX_DISTANCE_KM. */
public final class HyperlocalDistanceRule implements EligibilityRule {

    @Override
    public Optional<Rejection> check(PricingContext ctx) {
        HyperlocalOptions hl = ctx.request().hyperlocal();
        if (hl.distanceKm() == null) {
            return Optional.of(new Rejection(QuoteStatus.DISTANCE_REQUIRED,
                    "hyperlocal.distanceKm is required for same-city services"));
        }
        if (hl.vehicle() == null) {
            return Optional.of(new Rejection(QuoteStatus.VALIDATION_ERROR,
                    "hyperlocal.vehicle is required for same-city services (e.g. BIKE)"));
        }
        BigDecimal max = ctx.card().maxDistanceKm();
        if (max != null && hl.distanceKm().compareTo(max) > 0) {
            return Optional.of(new Rejection(QuoteStatus.NOT_SERVICEABLE,
                    "Distance " + hl.distanceKm().stripTrailingZeros().toPlainString()
                            + " km exceeds max " + max.stripTrailingZeros().toPlainString() + " km"));
        }
        return Optional.empty();
    }
}
