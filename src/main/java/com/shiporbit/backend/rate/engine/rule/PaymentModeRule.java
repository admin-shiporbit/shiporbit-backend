package com.shiporbit.backend.rate.engine.rule;

import com.shiporbit.backend.rate.core.QuoteStatus;
import com.shiporbit.backend.rate.engine.PincodeInfo;
import com.shiporbit.backend.rate.engine.PricingContext;
import com.shiporbit.backend.rate.engine.Rejection;

import java.util.Optional;

/** Destination must accept the payment mode. Runs after PincodeServiceabilityRule. */
// Lane rule 2: does the destination accept this payment mode?
public final class PaymentModeRule implements EligibilityRule {

    @Override
    // Check.
    public Optional<Rejection> check(PricingContext ctx) {
        // Destination row.
        PincodeInfo dest = ctx.lane().destination();
        // Missing?
        if (dest == null) {
            // Skip (already rejected by rule 1).
            return Optional.empty();       // reported by PincodeServiceabilityRule
        }
        // COD shipment and destination has no COD (e.g. every XpressBees B2B pincode)?
        if (ctx.request().isCod() && !dest.cod()) {
            // -> COD_NOT_AVAILABLE.
            return Optional.of(new Rejection(QuoteStatus.COD_NOT_AVAILABLE,
                    "COD not available at " + dest.pincode()));
        }
        // Prepaid shipment and destination has no prepaid?
        if (!ctx.request().isCod() && !dest.prepaid()) {
            // -> NOT_SERVICEABLE.
            return Optional.of(new Rejection(QuoteStatus.NOT_SERVICEABLE,
                    "Prepaid delivery not available at " + dest.pincode()));
        }
        // Passed.
        return Optional.empty();
    }
}
