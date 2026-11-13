package com.shiporbit.backend.rate.engine.rule;

import com.shiporbit.backend.rate.core.QuoteStatus;
import com.shiporbit.backend.rate.engine.PincodeInfo;
import com.shiporbit.backend.rate.engine.PricingContext;
import com.shiporbit.backend.rate.engine.Rejection;

import java.util.Optional;

/** HYPERLOCAL: origin and destination must share CITY_CODE. Missing rows are left to PincodeServiceabilityRule. */
// Hyperlocal model rule: both pincodes in the same city.
public final class SameCityRule implements EligibilityRule {

    @Override
    // Check.
    public Optional<Rejection> check(PricingContext ctx) {
        // Origin row.
        PincodeInfo origin = ctx.lane().origin();
        // Destination row.
        PincodeInfo dest = ctx.lane().destination();
        // Both present and city codes differ?
        if (origin != null && dest != null && !origin.cityCode().equalsIgnoreCase(dest.cityCode())) {
            // -> NOT_SERVICEABLE.
            return Optional.of(new Rejection(QuoteStatus.NOT_SERVICEABLE,
                    "Same-city delivery only: origin and destination are in different cities"));
        }
        // Passed.
        return Optional.empty();
    }
}
