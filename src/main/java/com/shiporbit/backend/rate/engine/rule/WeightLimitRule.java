package com.shiporbit.backend.rate.engine.rule;

import com.shiporbit.backend.rate.core.QuoteStatus;
import com.shiporbit.backend.rate.engine.PricingContext;
import com.shiporbit.backend.rate.engine.Rejection;

import java.math.BigDecimal;
import java.util.Optional;

/** Chargeable weight must not exceed the card's MAX_WEIGHT_KG. */
// Limit rule 1: chargeable weight within the card's maximum.
public final class WeightLimitRule implements EligibilityRule {

    @Override
    // Check.
    public Optional<Rejection> check(PricingContext ctx) {
        // Chargeable kg.
        BigDecimal chargeable = ctx.weight().chargeableWeightKg();
        // Card MAX_WEIGHT_KG.
        BigDecimal max = ctx.card().maxWeightKg();
        // Over the maximum?
        if (chargeable.compareTo(max) > 0) {
            // -> WEIGHT_LIMIT_EXCEEDED.
            return Optional.of(new Rejection(QuoteStatus.WEIGHT_LIMIT_EXCEEDED,
                    "Chargeable weight " + chargeable.stripTrailingZeros().toPlainString()
                            + " kg exceeds max " + max.stripTrailingZeros().toPlainString() + " kg"));
        }
        // Passed.
        return Optional.empty();
    }
}
