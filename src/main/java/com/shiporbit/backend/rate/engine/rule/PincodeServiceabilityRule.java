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
// Lane rule 1: does the partner serve both pincodes for this direction?
public final class PincodeServiceabilityRule implements EligibilityRule {

    @Override
    // Check.
    public Optional<Rejection> check(PricingContext ctx) {
        // Origin pincode.
        String originPin = ctx.request().originPincode();
        // Destination pincode.
        String destPin = ctx.request().destinationPincode();
        // Partner's origin row (null = not in SO_PARTNER_PINCODE).
        PincodeInfo origin = ctx.lane().origin();
        // Partner's destination row.
        PincodeInfo dest = ctx.lane().destination();

        // No origin row?
        if (origin == null) {
            // -> NOT_SERVICEABLE.
            return reject("Origin pincode " + originPin + " is not serviced");
        }
        // REVERSE shipment?
        if (ctx.request().direction() == ShipmentDirection.REVERSE) {
            // Origin must allow reverse pickup,
            if (!origin.reversePickup()) {
                // else NOT_SERVICEABLE.
                return reject("Reverse pickup not available at " + originPin);
            }
        // Otherwise origin must allow pickup,
        } else if (!origin.pickup()) {
            // else NOT_SERVICEABLE.
            return reject("Pickup not available at " + originPin);
        }
        // No destination row?
        if (dest == null) {
            // -> NOT_SERVICEABLE.
            return reject("Destination pincode " + destPin + " is not serviced");
        }
        // Destination must allow delivery,
        if (!dest.delivery()) {
            // else NOT_SERVICEABLE.
            return reject("Delivery not available at " + destPin);
        }
        // Passed.
        return Optional.empty();
    }

    // Rejection helper.
    private static Optional<Rejection> reject(String reason) {
        // NOT_SERVICEABLE with reason.
        return Optional.of(new Rejection(QuoteStatus.NOT_SERVICEABLE, reason));
    }
}
