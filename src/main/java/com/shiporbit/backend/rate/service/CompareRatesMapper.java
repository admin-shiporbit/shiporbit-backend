package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.rate.core.ChargeLine;
import com.shiporbit.backend.rate.core.ChargeableWeight;
import com.shiporbit.backend.rate.core.QuoteRequest;
import com.shiporbit.backend.rate.core.QuoteResult;
import com.shiporbit.backend.rate.core.ShipmentPackage;
import com.shiporbit.backend.rate.core.SlabInfo;
import com.shiporbit.backend.rate.core.WeightRules;
import com.shiporbit.backend.rate.dto.request.CompareRatesRequest;
import com.shiporbit.backend.rate.dto.response.CompareRatesResponse.Carrier;
import com.shiporbit.backend.rate.dto.response.CompareRatesResponse.Fare;
import com.shiporbit.backend.rate.dto.response.CompareRatesResponse.SortOption;
import com.shiporbit.backend.rate.engine.ChargeRule;
import com.shiporbit.backend.rate.engine.MarginTaxPolicy;
import com.shiporbit.backend.rate.engine.RateCardSnapshot;
import com.shiporbit.backend.rate.entity.CalcType;
import com.shiporbit.backend.rate.entity.MpsRule;
import com.shiporbit.backend.rate.entity.PaymentMode;
import com.shiporbit.backend.rate.entity.ServiceMode;
import com.shiporbit.backend.rate.entity.ShipmentDirection;
import com.shiporbit.backend.rate.routing.PartnerProfileProperties.Profile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Pure conversions between the frontend's compare format and the rate engine (no Spring, unit-tested).
 * Request: strings + units -> QuoteRequest (kg, cm). Response: one QuoteResult.Priced -> carrier + fare.
 */
public final class CompareRatesMapper {

    /** Divisor for the top-level volumetricWeight only; each partner's billable uses its own card. */
    public static final BigDecimal DISPLAY_VOLUMETRIC_DIVISOR = BigDecimal.valueOf(5000);
    private static final BigDecimal GRAMS_PER_KG = BigDecimal.valueOf(1000);
    private static final BigDecimal CM_PER_INCH = new BigDecimal("2.54");
    private static final BigDecimal CM_PER_METRE = BigDecimal.valueOf(100);
    private static final String COD = "COD";

    private CompareRatesMapper() { }

    // ---- request ----

    public static QuoteRequest toQuoteRequest(CompareRatesRequest r) {
        BigDecimal weightKg = positive("weight", new BigDecimal(r.weight()));
        if ("g".equalsIgnoreCase(r.weightUnit())) {
            weightKg = weightKg.divide(GRAMS_PER_KG, 3, RoundingMode.HALF_UP);
        }
        BigDecimal orderValue = r.orderValue() == null || r.orderValue().isBlank()
                ? BigDecimal.ZERO : new BigDecimal(r.orderValue());
        if (r.cod() && orderValue.signum() <= 0) {
            throw new IllegalArgumentException("orderValue must be greater than 0 when isCOD is true");
        }
        ShipmentPackage box = new ShipmentPackage(1, weightKg.setScale(3, RoundingMode.HALF_UP),
                cm("length", r.length(), r.dimensionUnit()), cm("width", r.width(), r.dimensionUnit()),
                cm("height", r.height(), r.dimensionUnit()));
        return new QuoteRequest(r.fromPin(), r.toPin(), ShipmentDirection.FORWARD,
                r.cod() ? PaymentMode.COD : PaymentMode.PREPAID,
                r.cod() ? orderValue : BigDecimal.ZERO,       // COD amount = order value collected on delivery
                orderValue,                                   // declared value (insurance / FOV)
                null, null, false, null, List.of(box), null, null, null, null);
    }

    private static BigDecimal cm(String field, String value, String unit) {
        BigDecimal v = positive(field, new BigDecimal(value));
        BigDecimal factor = switch (unit.toLowerCase(Locale.ROOT)) {
            case "in" -> CM_PER_INCH;
            case "m" -> CM_PER_METRE;
            default -> BigDecimal.ONE;
        };
        return v.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal positive(String field, BigDecimal v) {
        if (v.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be greater than 0");
        }
        return v;
    }

    /** Surface -> surface services, Air/Express -> air services, All/null -> both. Hyperlocal never (needs distance). */
    public static Set<ServiceMode> modesFor(String deliveryMode) {
        String mode = deliveryMode == null ? "all" : deliveryMode.toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "surface" -> EnumSet.of(ServiceMode.SURFACE, ServiceMode.SURFACE_AIR);
            case "air", "express" -> EnumSet.of(ServiceMode.AIR, ServiceMode.SURFACE_AIR);
            case "all" -> EnumSet.of(ServiceMode.SURFACE, ServiceMode.AIR, ServiceMode.SURFACE_AIR);
            default -> throw new IllegalArgumentException("deliveryMode must be Surface, Air, Express or All");
        };
    }

    /** Shipment-level weights shown at the top of the response (divisor 5000). */
    public static ChargeableWeight displayWeights(QuoteRequest request) {
        return ChargeableWeight.of(request.packages(),
                new WeightRules(DISPLAY_VOLUMETRIC_DIVISOR, null, BigDecimal.ZERO, MpsRule.TOTAL));
    }

    // ---- response ----

    /**
     * total = engine total (incl. margin + GST). cod = the COD charge's share of that total (COD line scaled by
     * the same margin and GST as everything else), shipping = total - cod, so the two always add up.
     */
    public static Fare fare(QuoteResult.Priced p, BigDecimal fallbackBillable) {
        BigDecimal preMargin = preMarginSubtotal(p);
        BigDecimal codLine = sum(p.breakdown(), COD);
        BigDecimal cod = preMargin.signum() > 0
                ? codLine.multiply(p.total()).divide(preMargin, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        BigDecimal billable = p.chargeableWeightKg() != null ? p.chargeableWeightKg() : fallbackBillable;
        return new Fare(p.total().subtract(cod), cod, p.gst(), p.total(), plain(billable), null);
    }

    /** Carrier block. card is null for live-API partners (no slab or COD rule to show). */
    public static Carrier carrier(QuoteResult.Priced p, Profile profile, RateCardSnapshot card) {
        BigDecimal factor = marginFactor(p);                 // card rates -> customer rates before GST
        SlabInfo slab = p.slab();
        BigDecimal baseRate = slab == null ? null : money(slab.firstRate().multiply(factor));
        BigDecimal perKg = slab == null || slab.additionalUnit().signum() == 0 ? null
                : money(slab.additionalRate().multiply(factor).divide(slab.additionalUnit(), 6, RoundingMode.HALF_UP));
        Optional<ChargeRule> codRule = card == null ? Optional.empty()
                : card.rules().stream().filter(r -> COD.equals(r.chargeCode())).findFirst();
        BigDecimal codFlat = codRule.map(r -> money((r.calcType() == CalcType.FLAT ? r.fixedAmount()
                : r.minAmount() != null ? r.minAmount() : r.fixedAmount()).multiply(factor))).orElse(null);
        BigDecimal codPct = codRule.map(r -> r.calcType() == CalcType.PERCENT ? money(r.rateValue().multiply(factor))
                : BigDecimal.ZERO.setScale(2)).orElse(null);
        return new Carrier(profile.displayName(), p.serviceCode(), profile.initials(), profile.color(),
                baseRate, perKg, codFlat, codPct, profile.etd(), profile.rating(),
                card == null ? null : plain(card.maxWeightKg()));
    }

    public static List<SortOption> sortOptions(boolean etdForEveryResult) {
        return List.of(
                new SortOption("fare", "Sort by fare", "result.total", "asc", true, null),
                new SortOption("speed", "Sort by delivery speed", "carrier.etd[0]", "asc", etdForEveryResult,
                        etdForEveryResult ? null : "Delivery time (ETD) is not configured for every partner."),
                new SortOption("distance", "Sort by distance", "result.distanceKm", "asc", false,
                        "Partner-specific distance data is not available."));
    }

    /** taxable / (taxable - margin): turns a pre-margin card rate into what the customer pays before GST. */
    static BigDecimal marginFactor(QuoteResult.Priced p) {
        BigDecimal preMargin = preMarginSubtotal(p);
        return preMargin.signum() > 0 ? p.taxableAmount().divide(preMargin, 10, RoundingMode.HALF_UP) : BigDecimal.ONE;
    }

    private static BigDecimal preMarginSubtotal(QuoteResult.Priced p) {
        return p.taxableAmount().subtract(sum(p.breakdown(), MarginTaxPolicy.MARGIN));
    }

    private static BigDecimal sum(List<ChargeLine> lines, String code) {
        return lines.stream().filter(l -> l.code().equals(code)).map(ChargeLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** 1.000 -> 1, 0.200 -> 0.2, 100 -> 100 (never 1E+2 in JSON). */
    public static BigDecimal plain(BigDecimal v) {
        if (v == null) {
            return null;
        }
        BigDecimal stripped = v.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
