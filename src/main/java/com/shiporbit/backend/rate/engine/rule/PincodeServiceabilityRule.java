package com.shiporbit.backend.rate.engine.rule;

import com.shiporbit.backend.rate.core.QuoteStatus;
import com.shiporbit.backend.rate.engine.PincodeInfo;
import com.shiporbit.backend.rate.engine.PricingContext;
import com.shiporbit.backend.rate.engine.Rejection;
import com.shiporbit.backend.rate.entity.ShipmentDirection;

import java.util.Optional;

/**
 * Origin must allow pickup (REVERSE: reverse pickup); destination must allow delivery.
 * No SO_PARTNER_PINCODE row = not serviced.
 */
public final class PincodeServiceabilityRule implements EligibilityRule {

    @Override
    public Optional<Rejection> check(PricingContext ctx) {
        String originPin = ctx.request().originPincode();
        String destPin = ctx.request().destinationPincode();
        PincodeInfo origin = ctx.lane().origin();
        PincodeInfo dest = ctx.lane().destination();

        if (origin == null) {
            return reject("Origin pincode " + originPin + " is not serviced");
        }
        if (ctx.request().direction() == ShipmentDirection.REVERSE) {
            if (!origin.reversePickup()) {
                return reject("Reverse pickup not available at " + originPin);
            }
        } else if (!origin.pickup()) {
            return reject("Pickup not available at " + originPin);
        }
        if (dest == null) {
            return reject("Destination pincode " + destPin + " is not serviced");
        }
        if (!dest.delivery()) {
            return reject("Delivery not available at " + destPin);
        }
        return Optional.empty();
    }

    private static Optional<Rejection> reject(String reason) {
        return Optional.of(new Rejection(QuoteStatus.NOT_SERVICEABLE, reason));
    }
}
