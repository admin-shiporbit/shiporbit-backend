package com.shiporbit.backend.rate.core;

import com.shiporbit.backend.rate.entity.MpsRule;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Card settings that decide chargeable weight (subset of SO_RATE_CARD).
 * volumetricDivisor (cm3 per kg, couriers) takes precedence over cftKg (kg per cubic foot, B2B).
 * Both null = dead weight only.
 */
public record WeightRules(BigDecimal volumetricDivisor, BigDecimal cftKg,
                          BigDecimal minChargeableWeightKg, MpsRule mpsRule) {

    public WeightRules {
        minChargeableWeightKg = minChargeableWeightKg == null ? BigDecimal.ZERO : minChargeableWeightKg;
        mpsRule = Objects.requireNonNullElse(mpsRule, MpsRule.TOTAL);
        if (volumetricDivisor != null && volumetricDivisor.signum() <= 0) {
            throw new IllegalArgumentException("volumetricDivisor must be > 0");
        }
        if (cftKg != null && cftKg.signum() <= 0) {
            throw new IllegalArgumentException("cftKg must be > 0");
        }
    }
}
