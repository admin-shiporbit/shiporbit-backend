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
public record QuoteRequest(
        @NotNull @Pattern(regexp = "\\d{6}", message = "must be a 6-digit pincode") String originPincode,
        @NotNull @Pattern(regexp = "\\d{6}", message = "must be a 6-digit pincode") String destinationPincode,
        @NotNull ShipmentDirection direction,
        @NotNull PaymentMode paymentMode,
        @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal codAmount,
        @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal declaredValue,
        RiskType riskType,
        @Size(max = 50) String productCategory,
        @JsonProperty("isDocument") Boolean document,   // JSON name kept as in the samples; null/missing = false
        LocalDate pickupDate,
        @NotEmpty @Size(max = 100) List<@Valid @NotNull ShipmentPackage> packages,
        Set<AddOn> addOns,
        Set<String> partners,
        @Valid B2bOptions b2b,
        @Valid HyperlocalOptions hyperlocal
) {
    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    public static final String DEFAULT_CATEGORY = "GENERAL";

    public QuoteRequest {
        codAmount = codAmount == null ? BigDecimal.ZERO : codAmount;
        riskType = riskType == null ? RiskType.OWNER : riskType;
        document = Boolean.TRUE.equals(document);
        productCategory = (productCategory == null || productCategory.isBlank())
                ? DEFAULT_CATEGORY : productCategory.trim().toUpperCase(Locale.ROOT);
        pickupDate = pickupDate == null ? LocalDate.now(IST) : pickupDate;
        packages = packages == null ? List.of() : List.copyOf(packages);
        addOns = addOns == null ? Set.of() : Set.copyOf(addOns);
        partners = partners == null ? Set.of() : partners.stream()
                .filter(p -> p != null && !p.isBlank())
                .map(p -> p.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        b2b = b2b == null ? B2bOptions.DEFAULT : b2b;
        hyperlocal = hyperlocal == null ? HyperlocalOptions.NONE : hyperlocal;
    }

    // ---- cross-field validation (Bean Validation calls these as properties) ----

    @JsonIgnore
    @AssertTrue(message = "codAmount must be greater than 0 when paymentMode is COD, and 0 when PREPAID")
    public boolean isCodAmountConsistent() {
        return paymentMode == null
                || (paymentMode == PaymentMode.COD ? codAmount.signum() > 0 : codAmount.signum() == 0);
    }

    // ---- derived values ----

    @JsonIgnore
    public boolean isCod() {
        return paymentMode == PaymentMode.COD;
    }

    public ShipmentType shipmentType() {
        return Boolean.TRUE.equals(document) ? ShipmentType.DOCUMENT : ShipmentType.PARCEL;
    }

    @JsonIgnore
    public boolean isSameCityPincode() {
        return originPincode.equals(destinationPincode);
    }

    public int totalPieces() {
        return packages.stream().mapToInt(ShipmentPackage::count).sum();
    }

    /** Empty partners filter = quote all partners. */
    public boolean includesPartner(String partnerName) {
        return partners.isEmpty() || partners.contains(partnerName.toUpperCase(Locale.ROOT));
    }
}
