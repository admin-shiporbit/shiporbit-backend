package com.shiporbit.backend.rate.engine.charge;

// Imports: request/lane/rule records, calc types, handlers.
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
// Spring bean.
@Component
// Applies a card's charge rules (SO_RATE_CHARGE_RULE) after base freight.
public class ChargeEngine {

    // Calc type -> handler (Strategy).
    private final Map<CalcType, ChargeHandler> handlers = new EnumMap<>(CalcType.class);

    // Spring injects every ChargeHandler bean (FLAT, PERCENT, PER_UNIT, TOP_UP).
    public ChargeEngine(List<ChargeHandler> handlerList) {
        // Register each by its calc type.
        handlerList.forEach(h -> handlers.put(h.calcType(), h));
        // Every calc type in the enum
        for (CalcType type : CalcType.values()) {
            // must have a handler,
            if (!handlers.containsKey(type)) {
                // else fail at startup.
                throw new IllegalStateException("No ChargeHandler for CALC_TYPE " + type);
            }
        }
    }

    /** @return BASE line followed by every applied charge, in SEQ order. */
    // Called from AbstractRateCardCalculator.quote() with x (kg / km) and base freight.
    public List<ChargeLine> apply(PricingContext ctx, BigDecimal chargeableUnits, BigDecimal baseFreight) {
        // Charge code -> amount, in insertion order.
        Map<String, BigDecimal> lines = new LinkedHashMap<>();
        // BASE line first.
        lines.put(ChargeLine.BASE, baseFreight);

        // Rules already sorted by SEQ (e.g. XpressBees: ODA 10/11, STATE_SURCHARGE 20, FUEL 30, FOV 50, WAYBILL 60, MIN_FREIGHT 70, GREEN_TAX 80, HEAVY_HANDLING 90):
        for (ChargeRule rule : ctx.card().rules()) {
            // skip if not valid on the pickup date,
            if (!rule.validOn(ctx.request().pickupDate())
                    // or not for this direction (FORWARD / RTO / REVERSE),
                    || !rule.appliesTo(ctx.request().direction())
                    // or its condition (APPLIES_WHEN) does not hold;
                    || !conditionHolds(rule, ctx.request(), ctx.lane())) {
                // next rule.
                continue;
            }
            // Base value for this rule (freight, sum of earlier lines, COD amount, declared value, weight ...).
            BigDecimal baseValue = baseValue(rule, ctx, lines);
            // Outside TIER_FROM..TIER_TO?
            if (!rule.inTier(baseValue)) {
                // Skip.
                continue;
            }
            // Amount from the handler, clamped to MIN_AMOUNT / MAX_AMOUNT,
            BigDecimal amount = clamp(handlers.get(rule.calcType()).amount(rule, baseValue, chargeableUnits), rule)
                    // rounded to 2 dp.
                    .setScale(2, RoundingMode.HALF_UP);
            // Add to the line (same code twice = tiers add up).
            lines.merge(rule.chargeCode(), amount, BigDecimal::add);
        }
        // Lines in order -> List<ChargeLine>.
        return lines.entrySet().stream().map(e -> new ChargeLine(e.getKey(), e.getValue())).toList();
    }

    // Does the rule's condition hold?
    private static boolean conditionHolds(ChargeRule rule, QuoteRequest req, PartnerLane lane) {
        // By APPLIES_WHEN:
        return switch (rule.appliesWhen()) {
            // ALWAYS: yes.
            case ALWAYS -> true;
            // COD: COD shipment.
            case COD -> req.isCod();
            // PREPAID: prepaid shipment.
            case PREPAID -> !req.isCod();
            // ODA_DESTINATION: destination pincode is ODA.
            case ODA_DESTINATION -> lane.destination() != null && lane.destination().oda();
            // ODA_PICKUP: origin pincode is ODA.
            case ODA_PICKUP -> lane.origin() != null && lane.origin().oda();
            // SPECIAL_CITY: destination flagged special (XpressBees: Kerala, J&K).
            case SPECIAL_CITY -> lane.destination() != null && lane.destination().specialCity();
            // RISK_OWNER: owner's risk (default).
            case RISK_OWNER -> req.riskType() == RiskType.OWNER;
            // RISK_CARRIER: carrier's risk.
            case RISK_CARRIER -> req.riskType() == RiskType.CARRIER;
            // ADDON_OTP: OTP add-on asked.
            case ADDON_OTP -> req.addOns().contains(AddOn.OTP);
            // ADDON_QC: QC add-on asked.
            case ADDON_QC -> req.addOns().contains(AddOn.QC);
            // ADDON_FRAGILE: fragile add-on asked.
            case ADDON_FRAGILE -> req.addOns().contains(AddOn.FRAGILE);
            // CATEGORY: product category equals APPLIES_VALUE.
            case CATEGORY -> rule.appliesValue() != null && rule.appliesValue().equalsIgnoreCase(req.productCategory());
            // DEST_STATE: destination state id equals APPLIES_VALUE
            case DEST_STATE -> rule.appliesValue() != null && lane.destination() != null
                    // (e.g. "32" = Delhi
                    && lane.destination().stateId() != null
                    // for the XpressBees green tax).
                    && rule.appliesValue().trim().equals(String.valueOf(lane.destination().stateId()));
            // PIECE_WEIGHT_OVER: any piece heavier than APPLIES_VALUE kg
            case PIECE_WEIGHT_OVER -> rule.appliesValue() != null && req.packages().stream()
                    // (XpressBees heavy handling: > 50 kg).
                    .anyMatch(p -> p.deadWeightKg().compareTo(new BigDecimal(rule.appliesValue().trim())) > 0);
        };
    }

    // Base value by BASE:
    private static BigDecimal baseValue(ChargeRule rule, PricingContext ctx, Map<String, BigDecimal> linesSoFar) {
        // by rule.base():
        return switch (rule.base()) {
            // BASE_FREIGHT: the BASE line.
            case BASE_FREIGHT -> linesSoFar.get(ChargeLine.BASE);
            // CHARGE_CODES: sum of the listed lines so far
            case CHARGE_CODES -> rule.baseCodes().stream()
                    // (missing code = 0),
                    .map(code -> linesSoFar.getOrDefault(code, BigDecimal.ZERO))
                    // e.g. "BASE,WAYBILL" for the XpressBees min freight.
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            // COD_AMOUNT: amount to collect.
            case COD_AMOUNT -> ctx.request().codAmount();
            // DECLARED_VALUE: order / invoice value.
            case DECLARED_VALUE -> ctx.request().declaredValue();
            // CHARGEABLE_WEIGHT: chargeable kg.
            case CHARGEABLE_WEIGHT -> ctx.weight().chargeableWeightKg();
            // ACTUAL_WEIGHT: total dead weight of all pieces
            case ACTUAL_WEIGHT -> ctx.request().packages().stream()
                    // (sum).
                    .map(p -> p.totalDeadWeightKg()).reduce(BigDecimal.ZERO, BigDecimal::add);
            // NONE: 0 (FLAT / PER_UNIT rules).
            case NONE -> BigDecimal.ZERO;
        };
    }

    // Apply MIN / MAX.
    private static BigDecimal clamp(BigDecimal amount, ChargeRule rule) {
        // Start with the handler amount.
        BigDecimal result = amount;
        // Below the minimum?
        if (rule.minAmount() != null && result.compareTo(rule.minAmount()) < 0) {
            // Raise to the minimum (e.g. ODA min Rs 800).
            result = rule.minAmount();
        }
        // Above the maximum?
        if (rule.maxAmount() != null && result.compareTo(rule.maxAmount()) > 0) {
            // Cap at the maximum.
            result = rule.maxAmount();
        }
        // Result.
        return result;
    }
}
