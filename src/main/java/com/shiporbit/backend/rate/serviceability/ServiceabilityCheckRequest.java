package com.shiporbit.backend.rate.serviceability;

import com.shiporbit.backend.rate.core.HyperlocalAddress;
import com.shiporbit.backend.rate.entity.PaymentMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.Set;

/**
 * POST /api/v1/rates/serviceability. weightKg decides Delhivery B2C (< 20 kg) vs LTL.
 * Dimensions (cm) are optional; used for Delhivery's chargeable weight only.
 * partners: DELHIVERY, SHREE_MARUTI (empty = all). hyperlocalPickup/hyperlocalDrop: optional, adds the
 * Shree Maruti hyperlocal check.
 */
public record ServiceabilityCheckRequest(
        @NotNull @Pattern(regexp = "\\d{6}") String originPincode,
        @NotNull @Pattern(regexp = "\\d{6}") String destinationPincode,
        @NotNull PaymentMode paymentMode,
        @NotNull @Positive BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        Set<String> partners,
        @Valid HyperlocalAddress hyperlocalPickup,
        @Valid HyperlocalAddress hyperlocalDrop
) {
    public boolean wants(String partner) {
        return partners == null || partners.isEmpty()
                || partners.stream().anyMatch(p -> p != null && p.trim().equalsIgnoreCase(partner));
    }
}
