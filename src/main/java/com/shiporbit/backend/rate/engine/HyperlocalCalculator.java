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
// Spring bean, registered for HYPERLOCAL. /compare filters hyperlocal results out (no distance input).
@Component
// Hyperlocal pricing by km.
public class HyperlocalCalculator extends AbstractRateCardCalculator {

    // Extra rules: same city, distance within card max.
    private static final List<EligibilityRule> RULES = List.of(new SameCityRule(), new HyperlocalDistanceRule());

    // Constructor injection.
    public HyperlocalCalculator(ChargeEngine chargeEngine, MarginTaxPolicy marginTaxPolicy) {
        // Pass to the base class.
        super(chargeEngine, marginTaxPolicy);
    }

    @Override
    // Models served.
    public Set<PricingModel> supportedModels() {
        // HYPERLOCAL.
        return EnumSet.of(PricingModel.HYPERLOCAL);
    }

    @Override
    // Model rules.
    protected List<EligibilityRule> modelRules() {
        // Same city + distance.
        return RULES;
    }

    @Override
    // Location key = vehicle type (e.g. BIKE).
    protected Optional<String> resolveLocation(PricingContext ctx) {
        // From request.hyperlocal.vehicle (null for /compare -> ZONE_UNKNOWN, but rules usually fail first).
        return Optional.ofNullable(ctx.request().hyperlocal().vehicle());
    }

    @Override
    // Reason when no vehicle.
    protected String missingLocationReason(PricingContext ctx) {
        // Message.
        return "hyperlocal.vehicle is required";
    }

    @Override
    // x = distance in km.
    protected BigDecimal chargeableUnits(PricingContext ctx) {
        // From request.hyperlocal.distanceKm.
        return ctx.request().hyperlocal().distanceKm();
    }

    @Override
    // Unit label.
    protected String unitLabel() {
        // "km".
        return "km";
    }
}
