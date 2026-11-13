package com.shiporbit.backend.rate.engine;

// Imports: request/result records, rules, charge engine, margin/tax policy, slab helpers.
import com.shiporbit.backend.rate.core.ChargeLine;
import com.shiporbit.backend.rate.core.ChargeableWeight;
import com.shiporbit.backend.rate.core.QuoteRequest;
import com.shiporbit.backend.rate.core.QuoteResult;
import com.shiporbit.backend.rate.core.QuoteStatus;
import com.shiporbit.backend.rate.core.SlabFreight;
import com.shiporbit.backend.rate.core.SlabInfo;
import com.shiporbit.backend.rate.engine.charge.ChargeEngine;
import com.shiporbit.backend.rate.engine.rule.EligibilityRule;
import com.shiporbit.backend.rate.engine.rule.PaymentModeRule;
import com.shiporbit.backend.rate.engine.rule.PieceWeightRule;
import com.shiporbit.backend.rate.engine.rule.PincodeServiceabilityRule;
import com.shiporbit.backend.rate.engine.rule.WeightLimitRule;
import com.shiporbit.backend.rate.entity.RtoRule;
import com.shiporbit.backend.rate.entity.ShipmentDirection;
import com.shiporbit.backend.rate.entity.SlabDirection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Template Method: the fixed pricing pipeline. Subclasses supply only what differs by pricing model:
 * extra eligibility rules, how the slab LOCATION_KEY is found, and the chargeable unit (kg or km).
 * <pre>
 * card checks -> chargeable weight -> eligibility (pincode, payment, model rules, weight)
 * -> location -> slab -> freight (+RTO %) -> charge rules (SEQ) -> min bill -> margin -> GST
 * </pre>
 */
// Base class of every rate-card calculator. quote() is final: the order of steps never changes.
public abstract class AbstractRateCardCalculator implements RateCardCalculator {

    /** Run first: is the lane served at all, and with this payment mode? */
    // Lane rules (run first):
    private static final List<EligibilityRule> LANE_RULES =
            // partner serves both pincodes (pickup at origin, delivery at destination), and supports the payment mode (COD / prepaid).
            List.of(new PincodeServiceabilityRule(), new PaymentModeRule());
    /** Run after model rules. */
    // Limit rules (run last): chargeable weight <= card max, every piece <= max piece weight.
    private static final List<EligibilityRule> LIMIT_RULES = List.of(new WeightLimitRule(), new PieceWeightRule());
    // 100, for percent maths.
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    // Code of the minimum-bill top-up line.
    public static final String MIN_BILL_TOPUP = "MIN_BILL_TOPUP";

    // Applies charge rules in SEQ order.
    private final ChargeEngine chargeEngine;
    // Adds ShipOrbit margin and GST.
    private final MarginTaxPolicy marginTaxPolicy;

    // Built by the subclass constructor (Spring injects both).
    protected AbstractRateCardCalculator(ChargeEngine chargeEngine, MarginTaxPolicy marginTaxPolicy) {
        // Store.
        this.chargeEngine = chargeEngine;
        // Store.
        this.marginTaxPolicy = marginTaxPolicy;
    }

    @Override
    // Step 3e-i: price one rate-card service. Called by RateQuoteService.price().
    public final QuoteResult quote(QuoteRequest req, RateCardSnapshot card, PartnerLane lane) {
        // Card says its rates already include GST?
        if (card.ratesIncludeGst()) {
            // -> Unavailable CONFIG_INCOMPLETE (not supported).
            return unavailable(card, QuoteStatus.CONFIG_INCOMPLETE,
                    "Rate cards with RATES_INCLUDE_GST = true are not supported yet");
        }
        // Card has no slabs?
        if (card.slabs().isEmpty()) {
            // -> Unavailable CONFIG_INCOMPLETE.
            return unavailable(card, QuoteStatus.CONFIG_INCOMPLETE, "Rate card " + card.versionLabel() + " has no slabs");
        }

        // Weights under this card's rules: dead, volumetric (L x W x H / divisor or CFT), chargeable = max(dead, volumetric, card minimum). See ChargeableWeight.of.
        ChargeableWeight weight = ChargeableWeight.of(req.packages(), card.weightRules());
        // Everything the rules need in one object.
        PricingContext ctx = new PricingContext(req, card, lane, weight);

        // Run rules in order: lane rules, then model rules (subclass), then limit rules;
        Optional<Rejection> rejection = Stream.of(LANE_RULES, modelRules(), LIMIT_RULES)
                // flatten to one list,
                .flatMap(List::stream)
                // check each one,
                .map(rule -> rule.check(ctx))
                // keep only failures,
                .flatMap(Optional::stream)
                // stop at the first failure.
                .findFirst();
        // A rule failed?
        if (rejection.isPresent()) {
            // -> Unavailable with that status (NOT_SERVICEABLE / COD_NOT_SUPPORTED / WEIGHT_LIMIT_EXCEEDED ...) and reason.
            return unavailable(card, rejection.get().status(), rejection.get().reason());
        }

        // Location key for the slab (subclass): zone / band / zone pair (e.g. XpressBees "W1-N1").
        Optional<String> location = resolveLocation(ctx);
        // None?
        if (location.isEmpty()) {
            // -> Unavailable ZONE_UNKNOWN.
            return unavailable(card, QuoteStatus.ZONE_UNKNOWN, missingLocationReason(ctx));
        }
        // RTO asked but the card does not offer RTO?
        if (req.direction() == ShipmentDirection.RTO && card.rtoRule() == RtoRule.NOT_APPLICABLE) {
            // -> Unavailable RATE_UNAVAILABLE. (/compare always sends FORWARD.)
            return unavailable(card, QuoteStatus.RATE_UNAVAILABLE, "RTO is not offered on this service");
        }

        // x for slab lookup and PER_UNIT charges: chargeable kg (ZoneMap) or km (Hyperlocal).
        BigDecimal units = chargeableUnits(ctx);
        // REVERSE shipments use REVERSE slabs; FORWARD and RTO use FORWARD slabs.
        SlabDirection slabDirection = req.direction() == ShipmentDirection.REVERSE ? SlabDirection.REVERSE : SlabDirection.FORWARD;
        // Pick the slab: direction + location key + x in (FROM_UNIT, TO_UNIT],
        Optional<SlabRow> slab = SlabSelector.pick(card.slabs(), slabDirection, location.get(), units,
                // most specific match for payment mode / shipment type wins.
                req.paymentMode(), req.shipmentType());
        // No slab?
        if (slab.isEmpty()) {
            // -> Unavailable RATE_UNAVAILABLE.
            return unavailable(card, QuoteStatus.RATE_UNAVAILABLE, "No " + slabDirection + " rate for "
                    + location.get() + " at " + units.stripTrailingZeros().toPlainString() + " " + unitLabel());
        }

        // The slab.
        SlabRow s = slab.get();
        // Base freight = first rate + steps x add rate (steps = ceil((x - first unit) / add unit) for CEIL_STEP).
        SlabFreight freight = SlabFreight.of(s.firstUnit(), s.firstRate(), s.addUnit(), s.addRate(), units, card.rounding());
        // RTO: x RTO %; otherwise unchanged.
        BigDecimal baseFreight = applyDirection(req, card, freight.amount());

        // Charge rules (fuel, COD, ODA, FOV, waybill, top-ups ...) in SEQ order, then top up to the card's MIN_BILL.
        List<ChargeLine> preTax = applyMinBill(chargeEngine.apply(ctx, units, baseFreight), card.minBill());
        // Margin on the pre-tax subtotal, then GST on (subtotal + margin).
        MarginTaxPolicy.Totals totals = marginTaxPolicy.apply(preTax);

        // Priced result: partner id, name,
        return new QuoteResult.Priced(card.service().partnerId(), card.service().partnerName(),
                // service code, mode, SAMPLE / CONTRACT, card version,
                card.service().serviceCode(), card.service().mode(), card.dataStatus(), card.versionLabel(),
                // location key, volumetric kg, chargeable kg,
                location.get(), weight.volumetricWeightKg(), weight.chargeableWeightKg(),
                // slab info (unit, first unit + rate, add unit, steps, add rate),
                new SlabInfo(s.unit(), s.firstUnit(), s.firstRate(), s.addUnit(), freight.additionalSteps(), s.addRate()),
                // lines (BASE ... MARGIN, GST), taxable, GST, total.
                totals.lines(), totals.taxableAmount(), totals.gst(), totals.total());
    }

    // ---- steps that differ by pricing model ----

    /** Extra eligibility rules, checked after pincode/payment rules and before the weight limit. */
    // Subclass: extra rules for its model.
    protected abstract List<EligibilityRule> modelRules();

    /** Slab LOCATION_KEY for this request: zone, distance band, zone pair or vehicle. */
    // Subclass: location key.
    protected abstract Optional<String> resolveLocation(PricingContext ctx);

    // Subclass: reason text when no location key.
    protected abstract String missingLocationReason(PricingContext ctx);

    /** x used for slab lookup and PER_UNIT charges: chargeable kg, or km for hyperlocal. */
    // Subclass: x (kg or km).
    protected abstract BigDecimal chargeableUnits(PricingContext ctx);

    // Subclass: "kg" / "km" for messages.
    protected abstract String unitLabel();

    // ---- shared steps ----

    /** RTO = forward freight x RTO_PERCENT (null = 100%). FORWARD and REVERSE use their own slabs as-is. */
    // RTO adjustment.
    private static BigDecimal applyDirection(QuoteRequest req, RateCardSnapshot card, BigDecimal freight) {
        // Not RTO, or no RTO %?
        if (req.direction() != ShipmentDirection.RTO || card.rtoPercent() == null) {
            // Freight as is.
            return freight;
        }
        // freight x RTO% / 100, 2 dp.
        return freight.multiply(card.rtoPercent()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /** Tops the pre-tax subtotal up to the card's MIN_BILL. */
    // Minimum bill.
    private static List<ChargeLine> applyMinBill(List<ChargeLine> lines, BigDecimal minBill) {
        // Pre-tax subtotal.
        BigDecimal subtotal = lines.stream().map(ChargeLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        // No minimum, or already above it?
        if (minBill == null || subtotal.compareTo(minBill) >= 0) {
            // Unchanged.
            return lines;
        }
        // Copy lines,
        List<ChargeLine> result = new ArrayList<>(lines);
        // add MIN_BILL_TOPUP = minimum - subtotal.
        result.add(new ChargeLine(MIN_BILL_TOPUP, minBill.subtract(subtotal)));
        // Return.
        return result;
    }

    // Unavailable result for this card.
    protected static QuoteResult.Unavailable unavailable(RateCardSnapshot card, QuoteStatus status, String reason) {
        // Service identity.
        ServiceInfo s = card.service();
        // Partner id, name, service code, mode,
        return new QuoteResult.Unavailable(s.partnerId(), s.partnerName(), s.serviceCode(), s.mode(),
                // data status, status, reason (error code derived from status).
                card.dataStatus(), status, reason);
    }
}
