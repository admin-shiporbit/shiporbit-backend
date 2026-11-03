package com.shiporbit.backend.rate.engine;

import com.shiporbit.backend.rate.engine.charge.ChargeEngine;
import com.shiporbit.backend.rate.engine.rule.EligibilityRule;
import com.shiporbit.backend.rate.engine.rule.HyperlocalDistanceRule;
import com.shiporbit.backend.rate.engine.rule.SameCityRule;
import com.shiporbit.backend.rate.entity.PricingModel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Same-day city delivery: same CITY_CODE only, priced by km. The slab LOCATION_KEY is the vehicle
 * (e.g. BIKE). No zone map. Weight still matters for the card's MAX_WEIGHT_KG.
 */
@Component
public class HyperlocalCalculator extends AbstractRateCardCalculator {

    private static final List<EligibilityRule> RULES = List.of(new SameCityRule(), new HyperlocalDistanceRule());

    public HyperlocalCalculator(ChargeEngine chargeEngine, MarginTaxPolicy marginTaxPolicy) {
        super(chargeEngine, marginTaxPolicy);
    }

    @Override
    public Set<PricingModel> supportedModels() {
        return EnumSet.of(PricingModel.HYPERLOCAL);
    }

    @Override
    protected List<EligibilityRule> modelRules() {
        return RULES;
    }

    @Override
    protected Optional<String> resolveLocation(PricingContext ctx) {
        return Optional.ofNullable(ctx.request().hyperlocal().vehicle());
    }

    @Override
    protected String missingLocationReason(PricingContext ctx) {
        return "hyperlocal.vehicle is required";
    }

    @Override
    protected BigDecimal chargeableUnits(PricingContext ctx) {
        return ctx.request().hyperlocal().distanceKm();
    }

    @Override
    protected String unitLabel() {
        return "km";
    }
}
