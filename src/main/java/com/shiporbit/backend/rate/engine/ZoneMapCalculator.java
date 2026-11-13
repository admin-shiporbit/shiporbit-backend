package com.shiporbit.backend.rate.engine;

import com.shiporbit.backend.rate.engine.charge.ChargeEngine;
import com.shiporbit.backend.rate.engine.rule.EligibilityRule;
import com.shiporbit.backend.rate.entity.PricingModel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Courier (ZONE_SLAB), India Post (DISTANCE_BAND) and B2B (B2B_FREIGHT): all three find the slab
 * LOCATION_KEY in SO_ZONE_MAP for origin -> destination and price by chargeable kg. They differ only
 * in data (zone names vs bands vs zone pairs; divisor vs CFT), so one calculator serves them.
 */
// Spring bean, registered for ZONE_SLAB, DISTANCE_BAND, B2B_FREIGHT (Amazon, Ekart, Pikndel, XpressBees ...).
@Component
// Courier / B2B pricing by chargeable kg.
public class ZoneMapCalculator extends AbstractRateCardCalculator {

    // Constructor injection.
    public ZoneMapCalculator(ChargeEngine chargeEngine, MarginTaxPolicy marginTaxPolicy) {
        // Pass to the base class.
        super(chargeEngine, marginTaxPolicy);
    }

    @Override
    // Models served.
    public Set<PricingModel> supportedModels() {
        // Three models.
        return EnumSet.of(PricingModel.ZONE_SLAB, PricingModel.DISTANCE_BAND, PricingModel.B2B_FREIGHT);
    }

    @Override
    // No extra rules.
    protected List<EligibilityRule> modelRules() {
        // Empty.
        return List.of();
    }

    @Override
    // Location key = the lane's key for this card's pricing model (from SO_ZONE_MAP override or zone rules, Step 3c).
    protected Optional<String> resolveLocation(PricingContext ctx) {
        // Missing -> empty -> ZONE_UNKNOWN.
        return Optional.ofNullable(ctx.lane().locationKeys().get(ctx.card().service().pricingModel()));
    }

    @Override
    // Reason when no key.
    protected String missingLocationReason(PricingContext ctx) {
        // "No B2B_FREIGHT mapping for
        return "No " + ctx.card().service().pricingModel() + " mapping for "
                // <origin> -> <destination>".
                + ctx.request().originPincode() + " -> " + ctx.request().destinationPincode();
    }

    @Override
    // x = chargeable kg.
    protected BigDecimal chargeableUnits(PricingContext ctx) {
        // From ChargeableWeight.
        return ctx.weight().chargeableWeightKg();
    }

    @Override
    // Unit label.
    protected String unitLabel() {
        // "kg".
        return "kg";
    }
}
