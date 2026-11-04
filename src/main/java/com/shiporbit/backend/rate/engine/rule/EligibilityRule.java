package com.shiporbit.backend.rate.engine.rule;

import com.shiporbit.backend.rate.engine.PricingContext;
import com.shiporbit.backend.rate.engine.Rejection;

import java.util.Optional;

/** Specification: can this service carry this shipment? Empty = yes. Stateless. */
public interface EligibilityRule {

    Optional<Rejection> check(PricingContext ctx);
}
