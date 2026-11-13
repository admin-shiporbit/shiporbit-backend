package com.shiporbit.backend.rate.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.shiporbit.backend.rate.entity.PaymentMode;
import com.shiporbit.backend.rate.entity.ShipmentDirection;
import com.shiporbit.backend.rate.entity.ShipmentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One quote request, priced against every enabled partner service.
 * Matches docs/samples/quote_request_*.json. Optional fields get defaults in the
 * compact constructor so calculators never see null for them.
 */
// Engine request. For /compare it is built in code (CompareRatesMapper), so these Bean Validation annotations are NOT run there - they apply to /quotes (@Valid body).
public record QuoteRequest(
        // Origin pincode, 6 digits.
        @NotNull @Pattern(regexp = "\\d{6}", message = "must be a 6-digit pincode") String originPincode,
        // Destination pincode, 6 digits.
        @NotNull @Pattern(regexp = "\\d{6}", message = "must be a 6-digit pincode") String destinationPincode,
        // FORWARD / RTO / REVERSE.
        @NotNull ShipmentDirection direction,
        // PREPAID / COD.
        @NotNull PaymentMode paymentMode,
        // Amount to collect on COD.
        @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal codAmount,
        // Declared / invoice value.
        @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal declaredValue,
        // OWNER / CARRIER risk.
        RiskType riskType,
        // Product category.
        @Size(max = 50) String productCategory,
        // Document or parcel.
        @JsonProperty("isDocument") Boolean document,   // JSON name kept as in the samples; null/missing = false
        // Pickup date (selects the card valid that day).
        LocalDate pickupDate,
        // Package lines.
        @NotEmpty @Size(max = 100) List<@Valid @NotNull ShipmentPackage> packages,
        // Add-ons (OTP, QC, FRAGILE).
        Set<AddOn> addOns,
        // Partner name filter (empty = all).
        Set<String> partners,
        // B2B options.
        @Valid B2bOptions b2b,
        // Hyperlocal options.
        @Valid HyperlocalOptions hyperlocal
) {
    // India time zone, for "today".
    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    // Default category.
    public static final String DEFAULT_CATEGORY = "GENERAL";

    // Compact constructor: defaults so calculators never see null.
    public QuoteRequest {
        // COD amount null -> 0.
        codAmount = codAmount == null ? BigDecimal.ZERO : codAmount;
        // Risk null -> OWNER.
        riskType = riskType == null ? RiskType.OWNER : riskType;
        // Document null -> false.
        document = Boolean.TRUE.equals(document);
        // Category blank -> GENERAL,
        productCategory = (productCategory == null || productCategory.isBlank())
                // else trimmed upper case.
                ? DEFAULT_CATEGORY : productCategory.trim().toUpperCase(Locale.ROOT);
        // Pickup date null -> today (IST).
        pickupDate = pickupDate == null ? LocalDate.now(IST) : pickupDate;
        // Packages immutable.
        packages = packages == null ? List.of() : List.copyOf(packages);
        // Add-ons null -> none.
        addOns = addOns == null ? Set.of() : Set.copyOf(addOns);
        // Partners: null -> empty, else
        partners = partners == null ? Set.of() : partners.stream()
                // drop blanks,
                .filter(p -> p != null && !p.isBlank())
                // trim + upper case,
                .map(p -> p.trim().toUpperCase(Locale.ROOT))
                // immutable set.
                .collect(Collectors.toUnmodifiableSet());
        // B2B null -> defaults (1 LR, no appointment / floor delivery).
        b2b = b2b == null ? B2bOptions.DEFAULT : b2b;
        // Hyperlocal null -> NONE.
        hyperlocal = hyperlocal == null ? HyperlocalOptions.NONE : hyperlocal;
    }

    // ---- cross-field validation (Bean Validation calls these as properties) ----

    @JsonIgnore
    @AssertTrue(message = "codAmount must be greater than 0 when paymentMode is COD, and 0 when PREPAID")
    // COD needs an amount > 0; prepaid needs 0.
    public boolean isCodAmountConsistent() {
        // No payment mode -> nothing to check,
        return paymentMode == null
                // else the rule above.
                || (paymentMode == PaymentMode.COD ? codAmount.signum() > 0 : codAmount.signum() == 0);
    }

    // ---- derived values ----

    @JsonIgnore
    // Is it COD?
    public boolean isCod() {
        // paymentMode == COD.
        return paymentMode == PaymentMode.COD;
    }

    // DOCUMENT or PARCEL (slab filter).
    public ShipmentType shipmentType() {
        // From the document flag.
        return Boolean.TRUE.equals(document) ? ShipmentType.DOCUMENT : ShipmentType.PARCEL;
    }

    @JsonIgnore
    // Same pincode both ends?
    public boolean isSameCityPincode() {
        // Equal strings.
        return originPincode.equals(destinationPincode);
    }

    // Total pieces.
    public int totalPieces() {
        // Sum of counts.
        return packages.stream().mapToInt(ShipmentPackage::count).sum();
    }

    /** Empty partners filter = quote all partners. */
    // Is this partner asked for?
    public boolean includesPartner(String partnerName) {
        // No filter -> yes; else name in the filter (upper case).
        return partners.isEmpty() || partners.contains(partnerName.toUpperCase(Locale.ROOT));
    }
}
