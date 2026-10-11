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
@Component
public class ZoneMapCalculator extends AbstractRateCardCalculator {

    public ZoneMapCalculator(ChargeEngine chargeEngine, MarginTaxPolicy marginTaxPolicy) {
        super(chargeEngine, marginTaxPolicy);
    }

    @Override
    public Set<PricingModel> supportedModels() {
        return EnumSet.of(PricingModel.ZONE_SLAB, PricingModel.DISTANCE_BAND, PricingModel.B2B_FREIGHT);
    }

    @Override
    protected List<EligibilityRule> modelRules() {
        return List.of();
    }

    @Override
    protected Optional<String> resolveLocation(PricingContext ctx) {
        return Optional.ofNullable(ctx.lane().locationKeys().get(ctx.card().service().pricingModel()));
    }

    @Override
    protected String missingLocationReason(PricingContext ctx) {
        return "No " + ctx.card().service().pricingModel() + " mapping for "
                + ctx.request().originPincode() + " -> " + ctx.request().destinationPincode();
    }

    @Override
    protected BigDecimal chargeableUnits(PricingContext ctx) {
        return ctx.weight().chargeableWeightKg();
    }

    @Override
    protected String unitLabel() {
        return "kg";
    }
}
