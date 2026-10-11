package com.shiporbit.backend.rate.core;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Identical boxes in one shipment: count x (dead weight, L x W x H).
 * Named ShipmentPackage, not Package, to avoid clashing with java.lang.Package.
 */
public record ShipmentPackage(
        @Min(1) @Max(1000) int count,
        @NotNull @Positive @Digits(integer = 7, fraction = 3) BigDecimal deadWeightKg,
        @NotNull @Positive @Digits(integer = 5, fraction = 2) BigDecimal lengthCm,
        @NotNull @Positive @Digits(integer = 5, fraction = 2) BigDecimal widthCm,
        @NotNull @Positive @Digits(integer = 5, fraction = 2) BigDecimal heightCm
) {
    /** Volume of one box in cm3. */
    public BigDecimal volumeCm3() {
        return lengthCm.multiply(widthCm).multiply(heightCm);
    }

    public BigDecimal totalDeadWeightKg() {
        return deadWeightKg.multiply(BigDecimal.valueOf(count));
    }

    public BigDecimal totalVolumeCm3() {
        return volumeCm3().multiply(BigDecimal.valueOf(count));
    }
}
