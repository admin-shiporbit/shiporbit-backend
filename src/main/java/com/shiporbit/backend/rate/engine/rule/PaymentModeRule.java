package com.shiporbit.backend.rate.engine.rule;

import com.shiporbit.backend.rate.core.QuoteStatus;
import com.shiporbit.backend.rate.engine.PincodeInfo;
import com.shiporbit.backend.rate.engine.PricingContext;
import com.shiporbit.backend.rate.engine.Rejection;

import java.util.Optional;

/** Destination must accept the payment mode. Runs after PincodeServiceabilityRule. */
public final class PaymentModeRule implements EligibilityRule {

    @Override
    public Optional<Rejection> check(PricingContext ctx) {
        PincodeInfo dest = ctx.lane().destination();
        if (dest == null) {
            return Optional.empty();       // reported by PincodeServiceabilityRule
        }
        if (ctx.request().isCod() && !dest.cod()) {
            return Optional.of(new Rejection(QuoteStatus.COD_NOT_AVAILABLE,
                    "COD not available at " + dest.pincode()));
        }
        if (!ctx.request().isCod() && !dest.prepaid()) {
            return Optional.of(new Rejection(QuoteStatus.NOT_SERVICEABLE,
                    "Prepaid delivery not available at " + dest.pincode()));
        }
        return Optional.empty();
    }
}
