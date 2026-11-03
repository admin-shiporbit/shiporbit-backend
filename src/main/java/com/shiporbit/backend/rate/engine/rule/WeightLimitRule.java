package com.shiporbit.backend.rate.engine.rule;

import com.shiporbit.backend.rate.core.QuoteStatus;
import com.shiporbit.backend.rate.engine.PricingContext;
import com.shiporbit.backend.rate.engine.Rejection;

import java.math.BigDecimal;
import java.util.Optional;

/** Chargeable weight must not exceed the card's MAX_WEIGHT_KG. */
public final class WeightLimitRule implements EligibilityRule {

    @Override
    public Optional<Rejection> check(PricingContext ctx) {
        BigDecimal chargeable = ctx.weight().chargeableWeightKg();
        BigDecimal max = ctx.card().maxWeightKg();
        if (chargeable.compareTo(max) > 0) {
            return Optional.of(new Rejection(QuoteStatus.WEIGHT_LIMIT_EXCEEDED,
                    "Chargeable weight " + chargeable.stripTrailingZeros().toPlainString()
                            + " kg exceeds max " + max.stripTrailingZeros().toPlainString() + " kg"));
        }
        return Optional.empty();
    }
}
