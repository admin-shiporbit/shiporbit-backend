package com.shiporbit.backend.rate.engine.charge;

import com.shiporbit.backend.rate.core.AddOn;
import com.shiporbit.backend.rate.core.ChargeLine;
import com.shiporbit.backend.rate.core.QuoteRequest;
import com.shiporbit.backend.rate.core.RiskType;
import com.shiporbit.backend.rate.engine.ChargeRule;
import com.shiporbit.backend.rate.engine.PartnerLane;
import com.shiporbit.backend.rate.engine.PricingContext;
import com.shiporbit.backend.rate.entity.CalcType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies a card's charge rules, in SEQ order, on top of base freight.
 * A rule is applied when: valid on the pickup date, covers the request direction,
 * its APPLIES_WHEN condition holds, and its base value is inside TIER_FROM..TIER_TO.
 * Amount = handler amount, clamped to MIN/MAX, rounded to 2 dp HALF_UP.
 * Rules with the same CHARGE_CODE (tiers) add into one line.
 * Zero lines are kept here (CHARGE_CODES bases may reference them); the response hides them.
 */
@Component
public class ChargeEngine {

    private final Map<CalcType, ChargeHandler> handlers = new EnumMap<>(CalcType.class);

    public ChargeEngine(List<ChargeHandler> handlerList) {
        handlerList.forEach(h -> handlers.put(h.calcType(), h));
        for (CalcType type : CalcType.values()) {
            if (!handlers.containsKey(type)) {
                throw new IllegalStateException("No ChargeHandler for CALC_TYPE " + type);
            }
        }
    }

    /** @return BASE line followed by every applied charge, in SEQ order. */
    public List<ChargeLine> apply(PricingContext ctx, BigDecimal chargeableUnits, BigDecimal baseFreight) {
        Map<String, BigDecimal> lines = new LinkedHashMap<>();
        lines.put(ChargeLine.BASE, baseFreight);

        for (ChargeRule rule : ctx.card().rules()) {
            if (!rule.validOn(ctx.request().pickupDate())
                    || !rule.appliesTo(ctx.request().direction())
                    || !conditionHolds(rule, ctx.request(), ctx.lane())) {
                continue;
            }
            BigDecimal baseValue = baseValue(rule, ctx, lines);
            if (!rule.inTier(baseValue)) {
                continue;
            }
            BigDecimal amount = clamp(handlers.get(rule.calcType()).amount(rule, baseValue, chargeableUnits), rule)
                    .setScale(2, RoundingMode.HALF_UP);
            lines.merge(rule.chargeCode(), amount, BigDecimal::add);
        }
        return lines.entrySet().stream().map(e -> new ChargeLine(e.getKey(), e.getValue())).toList();
    }

    private static boolean conditionHolds(ChargeRule rule, QuoteRequest req, PartnerLane lane) {
        return switch (rule.appliesWhen()) {
            case ALWAYS -> true;
            case COD -> req.isCod();
            case PREPAID -> !req.isCod();
            case ODA_DESTINATION -> lane.destination() != null && lane.destination().oda();
            case ODA_PICKUP -> lane.origin() != null && lane.origin().oda();
            case SPECIAL_CITY -> lane.destination() != null && lane.destination().specialCity();
            case RISK_OWNER -> req.riskType() == RiskType.OWNER;
            case RISK_CARRIER -> req.riskType() == RiskType.CARRIER;
            case ADDON_OTP -> req.addOns().contains(AddOn.OTP);
            case ADDON_QC -> req.addOns().contains(AddOn.QC);
            case ADDON_FRAGILE -> req.addOns().contains(AddOn.FRAGILE);
            case CATEGORY -> rule.appliesValue() != null && rule.appliesValue().equalsIgnoreCase(req.productCategory());
        };
    }

    private static BigDecimal baseValue(ChargeRule rule, PricingContext ctx, Map<String, BigDecimal> linesSoFar) {
        return switch (rule.base()) {
            case BASE_FREIGHT -> linesSoFar.get(ChargeLine.BASE);
            case CHARGE_CODES -> rule.baseCodes().stream()
                    .map(code -> linesSoFar.getOrDefault(code, BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            case COD_AMOUNT -> ctx.request().codAmount();
            case DECLARED_VALUE -> ctx.request().declaredValue();
            case CHARGEABLE_WEIGHT -> ctx.weight().chargeableWeightKg();
            case NONE -> BigDecimal.ZERO;
        };
    }

    private static BigDecimal clamp(BigDecimal amount, ChargeRule rule) {
        BigDecimal result = amount;
        if (rule.minAmount() != null && result.compareTo(rule.minAmount()) < 0) {
            result = rule.minAmount();
        }
        if (rule.maxAmount() != null && result.compareTo(rule.maxAmount()) > 0) {
            result = rule.maxAmount();
        }
        return result;
    }
}
