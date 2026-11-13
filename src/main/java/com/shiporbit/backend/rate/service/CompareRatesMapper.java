package com.shiporbit.backend.rate.service;

// Imports: engine records, compare DTO parts, rate-card types, partner profile.
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
import com.shiporbit.backend.rate.dto.response.CompareRatesResponse.FareLine;
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pure conversions between the frontend's compare format and the rate engine (no Spring, unit-tested).
 * Request: strings + units -> QuoteRequest (kg, cm). Response: one QuoteResult.Priced -> carrier + fare.
 */
// Static helpers only (no Spring bean, no state) - unit-tested in CompareRatesMapperTest.
public final class CompareRatesMapper {

    /** Divisor for the top-level volumetricWeight only; each partner's billable uses its own card. */
    // Volumetric divisor for the top-level (display) volumetric weight: L x W x H / 5000.
    public static final BigDecimal DISPLAY_VOLUMETRIC_DIVISOR = BigDecimal.valueOf(5000);
    // g -> kg.
    private static final BigDecimal GRAMS_PER_KG = BigDecimal.valueOf(1000);
    // inch -> cm.
    private static final BigDecimal CM_PER_INCH = new BigDecimal("2.54");
    // metre -> cm.
    private static final BigDecimal CM_PER_METRE = BigDecimal.valueOf(100);
    // Charge code of the COD line.
    private static final String COD = "COD";
    /**
     * Used when the request has no dimensions (blank or 0): a 1 x 1 x 1 cm box, so volumetric weight is ~0
     * (dead weight decides) and partners whose API requires dimensions still get positive values.
     */
    // 1.00 cm, used for L, W and H when dimensions are not sent.
    static final BigDecimal NO_DIMENSION_CM = BigDecimal.ONE.setScale(2);
    /**
     * Declared value used when orderValue is blank or 0 (prepaid). Partner APIs need a positive invoice value
     * (Delhivery LTL rejects inv_amount 0) and FOV / insurance rules are priced on it. COD still needs a real value.
     */
    // Rs 5,000 default declared value.
    static final BigDecimal DEFAULT_ORDER_VALUE = BigDecimal.valueOf(5000);

    /** Customer-facing labels for breakdown lines; unknown codes are shown title-cased ("STATE_SURCHARGE" -> "State surcharge"). */
    // Code -> label map for breakdown lines shown to the customer.
    private static final Map<String, String> LINE_LABELS = Map.ofEntries(
            Map.entry(ChargeLine.BASE, "Freight"),
            Map.entry("FUEL_SURCHARGE", "Fuel surcharge"),
            Map.entry("FUEL_HIKE", "Fuel hike"),
            Map.entry(COD, "COD charge"),
            Map.entry("ODA", "Remote area (ODA) charge"),
            Map.entry("ODA_DESTINATION", "Remote area (ODA) charge"),
            Map.entry("FOV", "Insurance (FOV)"),
            Map.entry("FOV_OWNER", "Insurance (FOV)"),
            Map.entry("FOV_CARRIER", "Insurance (FOV)"),
            Map.entry("ROV", "Insurance (ROV)"),
            Map.entry("WAYBILL", "Waybill charge"),
            Map.entry("DOCKET", "Docket charge"),
            Map.entry("HANDLING", "Handling charges"),
            Map.entry("ADJUSTMENT", "Other charges"),
            Map.entry("MIN_FREIGHT", "Minimum freight top-up"),
            Map.entry("STATE_SURCHARGE", "State surcharge"),
            Map.entry("GREEN_TAX", "Green tax (Delhi)"),
            Map.entry("HEAVY_HANDLING", "Heavy piece handling"),
            Map.entry(MarginTaxPolicy.GST, "GST"));

    // No instances.
    private CompareRatesMapper() { }

    // ---- request ----

    // Step 1b (called from RateCompareService). Request DTO -> QuoteRequest.
    public static QuoteRequest toQuoteRequest(CompareRatesRequest r) {
        // Parse weight string; must be > 0 or IllegalArgumentException -> 400.
        BigDecimal weightKg = positive("weight", new BigDecimal(r.weight()));
        // Weight sent in grams?
        if ("g".equalsIgnoreCase(r.weightUnit())) {
            // Convert to kg, 3 decimals.
            weightKg = weightKg.divide(GRAMS_PER_KG, 3, RoundingMode.HALF_UP);
        }
        // Order value as sent: blank / missing -> 0,
        BigDecimal orderValue = r.orderValue() == null || r.orderValue().isBlank()
                // otherwise parse it.
                ? BigDecimal.ZERO : new BigDecimal(r.orderValue());
        // COD needs an amount to collect.
        if (r.cod() && orderValue.signum() <= 0) {
            // -> 400 "orderValue must be greater than 0 when isCOD is true".
            throw new IllegalArgumentException("orderValue must be greater than 0 when isCOD is true");
        }
        // Length in cm (null when blank / 0).
        BigDecimal length = cm(r.length(), r.dimensionUnit());
        // Width in cm.
        BigDecimal width = cm(r.width(), r.dimensionUnit());
        // Height in cm.
        BigDecimal height = cm(r.height(), r.dimensionUnit());
        // If any one is missing, ignore all three (partial dimensions are not trusted).
        if (length == null || width == null || height == null) {       // any dimension missing -> none used
            // 1 cm,
            length = NO_DIMENSION_CM;
            // 1 cm,
            width = NO_DIMENSION_CM;
            // 1 cm -> volumetric ~0, dead weight decides.
            height = NO_DIMENSION_CM;
        }
        // One package: count 1, dead weight (3 dp), L, W, H in cm. The website always sends one box with the total weight.
        ShipmentPackage box = new ShipmentPackage(1, weightKg.setScale(3, RoundingMode.HALF_UP), length, width, height);
        // Build the engine request: origin, destination, FORWARD,
        return new QuoteRequest(r.fromPin(), r.toPin(), ShipmentDirection.FORWARD,
                // payment mode COD or PREPAID,
                r.cod() ? PaymentMode.COD : PaymentMode.PREPAID,
                // COD amount (only when COD),
                r.cod() ? orderValue : BigDecimal.ZERO,       // COD amount = order value collected on delivery
                // declared value (FOV / insurance rules, Delhivery inv_amount): order value, or Rs 5,000 when blank / 0,
                orderValue.signum() > 0 ? orderValue : DEFAULT_ORDER_VALUE,
                // riskType null -> OWNER, category null -> GENERAL, not a document, pickupDate null -> today IST, the package, no add-ons, all partners, default B2B / hyperlocal options (defaults set in QuoteRequest's constructor).
                null, null, false, null, List.of(box), null, null, null, null);
    }

    /** Dimension in cm (2 dp), or null when blank / 0 (= not provided). */
    // Dimension string + unit -> cm.
    private static BigDecimal cm(String value, String unit) {
        // Blank -> not provided.
        if (value == null || value.isBlank()) {
            // null.
            return null;
        }
        // Parse.
        BigDecimal v = new BigDecimal(value.trim());
        // 0 or negative -> not provided.
        if (v.signum() <= 0) {
            // null.
            return null;
        }
        // Unit factor:
        BigDecimal factor = switch (unit.toLowerCase(Locale.ROOT)) {
            // inches,
            case "in" -> CM_PER_INCH;
            // metres,
            case "m" -> CM_PER_METRE;
            // anything else is treated as cm.
            default -> BigDecimal.ONE;
        };
        // Convert and round to 2 dp.
        return v.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    // Guard: value must be > 0.
    private static BigDecimal positive(String field, BigDecimal v) {
        // Zero or negative?
        if (v.signum() <= 0) {
            // -> IllegalArgumentException (400).
            throw new IllegalArgumentException(field + " must be greater than 0");
        }
        // Valid value.
        return v;
    }

    /** Surface -> surface services, Air/Express -> air services, All/null -> both. Hyperlocal never (needs distance). */
    // Step 1a. deliveryMode -> modes kept in the response.
    public static Set<ServiceMode> modesFor(String deliveryMode) {
        // null -> "all"; lower-case for matching.
        String mode = deliveryMode == null ? "all" : deliveryMode.toLowerCase(Locale.ROOT);
        // Choose by mode:
        return switch (mode) {
            // Surface -> SURFACE and SURFACE_AIR services.
            case "surface" -> EnumSet.of(ServiceMode.SURFACE, ServiceMode.SURFACE_AIR);
            // Air / Express -> AIR and SURFACE_AIR services.
            case "air", "express" -> EnumSet.of(ServiceMode.AIR, ServiceMode.SURFACE_AIR);
            // All -> every mode except HYPERLOCAL (hyperlocal needs a distance the website does not send).
            case "all" -> EnumSet.of(ServiceMode.SURFACE, ServiceMode.AIR, ServiceMode.SURFACE_AIR);
            // Anything else -> 400.
            default -> throw new IllegalArgumentException("deliveryMode must be Surface, Air, Express or All");
        };
    }

    /** Shipment-level weights shown at the top of the response (divisor 5000). */
    // Step 1c. Display weights at the top of the response:
    public static ChargeableWeight displayWeights(QuoteRequest request) {
        // ChargeableWeight.of(packages, rules) with
        return ChargeableWeight.of(request.packages(),
                // divisor 5000, no CFT, no minimum, MPS rule TOTAL.
                new WeightRules(DISPLAY_VOLUMETRIC_DIVISOR, null, BigDecimal.ZERO, MpsRule.TOTAL));
    }

    // ---- response ----

    /**
     * total = engine total (incl. margin + GST). cod = the COD charge's share of that total (COD line scaled by
     * the same margin and GST as everything else), shipping = total - cod, so the two always add up.
     */
    // Fare block for one priced result (called in Step 3 loop).
    public static Fare fare(QuoteResult.Priced p, BigDecimal fallbackBillable) {
        // Subtotal before ShipOrbit margin = taxable - MARGIN line.
        BigDecimal preMargin = preMarginSubtotal(p);
        // The partner's COD line (0 when prepaid).
        BigDecimal codLine = sum(p.breakdown(), COD);
        // COD share of the final total = COD line x total / pre-margin subtotal (same margin + GST scaling as other lines)
        BigDecimal cod = preMargin.signum() > 0
                // -> rounded to 2 dp,
                ? codLine.multiply(p.total()).divide(preMargin, 2, RoundingMode.HALF_UP)
                // or 0 when the subtotal is 0.
                : BigDecimal.ZERO.setScale(2);
        // Billable weight = partner's chargeable weight; live APIs may not give one -> shipment-level fallback.
        BigDecimal billable = p.chargeableWeightKg() != null ? p.chargeableWeightKg() : fallbackBillable;
        // shipping = total - cod; then cod, GST, total, billable, distance (null), breakdown lines.
        return new Fare(p.total().subtract(cod), cod, p.gst(), p.total(), plain(billable), null, breakdown(p));
    }

    /**
     * Customer-facing price lines that add up exactly to total:
     * every partner/rate-card line (BASE, fuel, COD, ODA ...) with ShipOrbit margin spread proportionally
     * (x taxable / pre-margin subtotal, the same factor as baseRate / perKg), then one GST line.
     * MARGIN is never shown as its own line. Rounding difference goes to the largest line, so
     * sum(lines except GST) == taxableAmount and sum(all lines) == total.
     */
    // Customer-facing breakdown lines.
    static List<FareLine> breakdown(QuoteResult.Priced p) {
        // Margin factor = taxable / pre-margin subtotal (e.g. 1.10 for 10% margin).
        BigDecimal factor = marginFactor(p);
        // Output lines.
        List<FareLine> lines = new ArrayList<>();
        // Index of the largest line (gets any rounding difference).
        int largest = -1;
        // Running sum of the output lines.
        BigDecimal sum = BigDecimal.ZERO;
        // For every engine line (BASE, FUEL, COD, ODA, ..., MARGIN, GST):
        for (ChargeLine line : p.breakdown()) {
            // skip MARGIN (spread into the others), skip GST (added last),
            if (MarginTaxPolicy.MARGIN.equals(line.code()) || MarginTaxPolicy.GST.equals(line.code())
                    // and skip zero lines (e.g. MIN_FREIGHT top-up that came to 0).
                    || line.amount().signum() == 0) {
                // Next line.
                continue;
            }
            // Line amount incl. its share of margin, 2 dp.
            BigDecimal amount = money(line.amount().multiply(factor));
            // Add with its label.
            lines.add(new FareLine(line.code(), label(line.code()), amount));
            // Add to running sum.
            sum = sum.add(amount);
            // Bigger than the current largest?
            if (largest < 0 || amount.compareTo(lines.get(largest).amount()) > 0) {
                // Remember its index.
                largest = lines.size() - 1;
            }
        }
        // Rounding gap = taxable (2 dp) - sum of rounded lines (a few paise at most).
        BigDecimal diff = p.taxableAmount().setScale(2, RoundingMode.HALF_UP).subtract(sum);
        // If there is a gap and at least one line,
        if (largest >= 0 && diff.signum() != 0) {
            // take the largest line
            FareLine l = lines.get(largest);
            // and add the gap to it, so lines add up to taxable exactly.
            lines.set(largest, new FareLine(l.code(), l.label(), l.amount().add(diff)));
        }
        // GST present?
        if (p.gst() != null && p.gst().signum() != 0) {
            // Add it as the last line.
            lines.add(new FareLine(MarginTaxPolicy.GST, label(MarginTaxPolicy.GST), p.gst().setScale(2, RoundingMode.HALF_UP)));
        }
        // Immutable result.
        return List.copyOf(lines);
    }

    // Code -> label.
    static String label(String code) {
        // Fixed label for known codes.
        String known = LINE_LABELS.get(code);
        // Known?
        if (known != null) {
            // Use it.
            return known;
        }
        // Unknown: "STATE_SURCHARGE" -> "state surcharge",
        String words = code.replace('_', ' ').toLowerCase(Locale.ROOT);
        // -> "State surcharge" (first letter upper-case; empty code returned as is).
        return words.isEmpty() ? code : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /** Carrier block. card is null for live-API partners (no slab or COD rule to show). */
    // Carrier block for one priced result.
    public static Carrier carrier(QuoteResult.Priced p, Profile profile, RateCardSnapshot card) {
        // Same margin factor as the fare lines.
        BigDecimal factor = marginFactor(p);                 // card rates -> customer rates before GST
        // Slab used (null for live APIs).
        SlabInfo slab = p.slab();
        // Base rate shown = slab first rate x margin factor.
        BigDecimal baseRate = slab == null ? null : money(slab.firstRate().multiply(factor));
        // Per-kg shown = additional rate x factor / additional unit;
        BigDecimal perKg = slab == null || slab.additionalUnit().signum() == 0 ? null
                // null when there is no slab or no additional step.
                : money(slab.additionalRate().multiply(factor).divide(slab.additionalUnit(), 6, RoundingMode.HALF_UP));
        // COD rule of the card (none for live APIs)
        Optional<ChargeRule> codRule = card == null ? Optional.empty()
                // = first rule with charge code COD.
                : card.rules().stream().filter(r -> COD.equals(r.chargeCode())).findFirst();
        // Flat COD shown: FLAT -> fixed amount, else the minimum (or fixed), x factor;
        BigDecimal codFlat = codRule.map(r -> money((r.calcType() == CalcType.FLAT ? r.fixedAmount()
                // null when no COD rule.
                : r.minAmount() != null ? r.minAmount() : r.fixedAmount()).multiply(factor))).orElse(null);
        // COD % shown: PERCENT rule -> rate x factor,
        BigDecimal codPct = codRule.map(r -> r.calcType() == CalcType.PERCENT ? money(r.rateValue().multiply(factor))
                // otherwise 0; null when no COD rule.
                : BigDecimal.ZERO.setScale(2)).orElse(null);
        // Carrier record: display name, service code, initials, colour,
        return new Carrier(profile.displayName(), p.serviceCode(), profile.initials(), profile.color(),
                // base rate, per kg, COD flat, COD %, ETD, rating,
                baseRate, perKg, codFlat, codPct, profile.etd(), profile.rating(),
                // max weight from the card (null for live APIs).
                card == null ? null : plain(card.maxWeightKg()));
    }

    // Sort options shown by the UI:
    public static List<SortOption> sortOptions(boolean etdForEveryResult) {
        // three options,
        return List.of(
                // fare (always enabled),
                new SortOption("fare", "Sort by fare", "result.total", "asc", true, null),
                // speed (enabled only when every result has ETD),
                new SortOption("speed", "Sort by delivery speed", "carrier.etd[0]", "asc", etdForEveryResult,
                        // with the reason when disabled,
                        etdForEveryResult ? null : "Delivery time (ETD) is not configured for every partner."),
                // distance (always disabled),
                new SortOption("distance", "Sort by distance", "result.distanceKm", "asc", false,
                        // with the reason.
                        "Partner-specific distance data is not available."));
    }

    /** taxable / (taxable - margin): turns a pre-margin card rate into what the customer pays before GST. */
    // taxable / (taxable - margin).
    static BigDecimal marginFactor(QuoteResult.Priced p) {
        // Pre-margin subtotal.
        BigDecimal preMargin = preMarginSubtotal(p);
        // Divide (10 dp), or 1 when subtotal is 0.
        return preMargin.signum() > 0 ? p.taxableAmount().divide(preMargin, 10, RoundingMode.HALF_UP) : BigDecimal.ONE;
    }

    // taxable - MARGIN line
    private static BigDecimal preMarginSubtotal(QuoteResult.Priced p) {
        // = subtotal before margin.
        return p.taxableAmount().subtract(sum(p.breakdown(), MarginTaxPolicy.MARGIN));
    }

    // Sum of all lines with this code
    private static BigDecimal sum(List<ChargeLine> lines, String code) {
        // (filter by code, add amounts).
        return lines.stream().filter(l -> l.code().equals(code)).map(ChargeLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Round to paise
    private static BigDecimal money(BigDecimal v) {
        // (2 dp, HALF_UP).
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** 1.000 -> 1, 0.200 -> 0.2, 100 -> 100 (never 1E+2 in JSON). */
    // BigDecimal without trailing zeros for JSON.
    public static BigDecimal plain(BigDecimal v) {
        // null in
        if (v == null) {
            // -> null out.
            return null;
        }
        // 1.000 -> 1, 0.200 -> 0.2.
        BigDecimal stripped = v.stripTrailingZeros();
        // 100 -> 1E+2 after strip, so set scale 0 to print 100.
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
