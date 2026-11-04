package com.shiporbit.backend.rate.engine.rule;

import com.shiporbit.backend.rate.core.QuoteStatus;
import com.shiporbit.backend.rate.engine.PincodeInfo;
import com.shiporbit.backend.rate.engine.PricingContext;
import com.shiporbit.backend.rate.engine.Rejection;

import java.util.Optional;

/** HYPERLOCAL: origin and destination must share CITY_CODE. Missing rows are left to PincodeServiceabilityRule. */
public final class SameCityRule implements EligibilityRule {

    @Override
    public Optional<Rejection> check(PricingContext ctx) {
        PincodeInfo origin = ctx.lane().origin();
        PincodeInfo dest = ctx.lane().destination();
        if (origin != null && dest != null && !origin.cityCode().equalsIgnoreCase(dest.cityCode())) {
            return Optional.of(new Rejection(QuoteStatus.NOT_SERVICEABLE,
                    "Same-city delivery only: origin and destination are in different cities"));
        }
        return Optional.empty();
    }
}
