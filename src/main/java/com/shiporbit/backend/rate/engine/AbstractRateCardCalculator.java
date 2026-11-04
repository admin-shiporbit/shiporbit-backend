package com.shiporbit.backend.rate.engine;

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
public abstract class AbstractRateCardCalculator implements RateCardCalculator {

    /** Run first: is the lane served at all, and with this payment mode? */
    private static final List<EligibilityRule> LANE_RULES =
            List.of(new PincodeServiceabilityRule(), new PaymentModeRule());
    /** Run after model rules. */
    private static final List<EligibilityRule> LIMIT_RULES = List.of(new WeightLimitRule());
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    public static final String MIN_BILL_TOPUP = "MIN_BILL_TOPUP";

    private final ChargeEngine chargeEngine;
    private final MarginTaxPolicy marginTaxPolicy;

    protected AbstractRateCardCalculator(ChargeEngine chargeEngine, MarginTaxPolicy marginTaxPolicy) {
        this.chargeEngine = chargeEngine;
        this.marginTaxPolicy = marginTaxPolicy;
    }

    @Override
    public final QuoteResult quote(QuoteRequest req, RateCardSnapshot card, PartnerLane lane) {
        if (card.ratesIncludeGst()) {
            return unavailable(card, QuoteStatus.CONFIG_INCOMPLETE,
                    "Rate cards with RATES_INCLUDE_GST = true are not supported yet");
        }
        if (card.slabs().isEmpty()) {
            return unavailable(card, QuoteStatus.CONFIG_INCOMPLETE, "Rate card " + card.versionLabel() + " has no slabs");
        }

        ChargeableWeight weight = ChargeableWeight.of(req.packages(), card.weightRules());
        PricingContext ctx = new PricingContext(req, card, lane, weight);

        Optional<Rejection> rejection = Stream.of(LANE_RULES, modelRules(), LIMIT_RULES)
                .flatMap(List::stream)
                .map(rule -> rule.check(ctx))
                .flatMap(Optional::stream)
                .findFirst();
        if (rejection.isPresent()) {
            return unavailable(card, rejection.get().status(), rejection.get().reason());
        }

        Optional<String> location = resolveLocation(ctx);
        if (location.isEmpty()) {
            return unavailable(card, QuoteStatus.ZONE_UNKNOWN, missingLocationReason(ctx));
        }
        if (req.direction() == ShipmentDirection.RTO && card.rtoRule() == RtoRule.NOT_APPLICABLE) {
            return unavailable(card, QuoteStatus.RATE_UNAVAILABLE, "RTO is not offered on this service");
        }

        BigDecimal units = chargeableUnits(ctx);
        SlabDirection slabDirection = req.direction() == ShipmentDirection.REVERSE ? SlabDirection.REVERSE : SlabDirection.FORWARD;
        Optional<SlabRow> slab = SlabSelector.pick(card.slabs(), slabDirection, location.get(), units,
                req.paymentMode(), req.shipmentType());
        if (slab.isEmpty()) {
            return unavailable(card, QuoteStatus.RATE_UNAVAILABLE, "No " + slabDirection + " rate for "
                    + location.get() + " at " + units.stripTrailingZeros().toPlainString() + " " + unitLabel());
        }

        SlabRow s = slab.get();
        SlabFreight freight = SlabFreight.of(s.firstUnit(), s.firstRate(), s.addUnit(), s.addRate(), units, card.rounding());
        BigDecimal baseFreight = applyDirection(req, card, freight.amount());

        List<ChargeLine> preTax = applyMinBill(chargeEngine.apply(ctx, units, baseFreight), card.minBill());
        MarginTaxPolicy.Totals totals = marginTaxPolicy.apply(preTax);

        return new QuoteResult.Priced(card.service().partnerId(), card.service().partnerName(),
                card.service().serviceCode(), card.service().mode(), card.dataStatus(), card.versionLabel(),
                location.get(), weight.volumetricWeightKg(), weight.chargeableWeightKg(),
                new SlabInfo(s.unit(), s.firstUnit(), s.firstRate(), s.addUnit(), freight.additionalSteps(), s.addRate()),
                totals.lines(), totals.taxableAmount(), totals.gst(), totals.total());
    }

    // ---- steps that differ by pricing model ----

    /** Extra eligibility rules, checked after pincode/payment rules and before the weight limit. */
    protected abstract List<EligibilityRule> modelRules();

    /** Slab LOCATION_KEY for this request: zone, distance band, zone pair or vehicle. */
    protected abstract Optional<String> resolveLocation(PricingContext ctx);

    protected abstract String missingLocationReason(PricingContext ctx);

    /** x used for slab lookup and PER_UNIT charges: chargeable kg, or km for hyperlocal. */
    protected abstract BigDecimal chargeableUnits(PricingContext ctx);

    protected abstract String unitLabel();

    // ---- shared steps ----

    /** RTO = forward freight x RTO_PERCENT (null = 100%). FORWARD and REVERSE use their own slabs as-is. */
    private static BigDecimal applyDirection(QuoteRequest req, RateCardSnapshot card, BigDecimal freight) {
        if (req.direction() != ShipmentDirection.RTO || card.rtoPercent() == null) {
            return freight;
        }
        return freight.multiply(card.rtoPercent()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /** Tops the pre-tax subtotal up to the card's MIN_BILL. */
    private static List<ChargeLine> applyMinBill(List<ChargeLine> lines, BigDecimal minBill) {
        BigDecimal subtotal = lines.stream().map(ChargeLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (minBill == null || subtotal.compareTo(minBill) >= 0) {
            return lines;
        }
        List<ChargeLine> result = new ArrayList<>(lines);
        result.add(new ChargeLine(MIN_BILL_TOPUP, minBill.subtract(subtotal)));
        return result;
    }

    protected static QuoteResult.Unavailable unavailable(RateCardSnapshot card, QuoteStatus status, String reason) {
        ServiceInfo s = card.service();
        return new QuoteResult.Unavailable(s.partnerId(), s.partnerName(), s.serviceCode(), s.mode(),
                card.dataStatus(), status, reason);
    }
}
