package com.shiporbit.backend.rate.core;

import com.shiporbit.backend.rate.entity.MpsRule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Chargeable weight = max(dead weight, volumetric weight, card minimum).
 * <ul>
 *   <li>Courier: volumetric = L x W x H / divisor (e.g. 5000).</li>
 *   <li>B2B: volumetric = L x W x H / 28,316.846592 (cm3 per cubic foot) x CFT kg.</li>
 *   <li>MPS TOTAL: compare shipment totals. PER_PIECE: max per box, then sum.</li>
 * </ul>
 * All weights in kg, 3 decimals, HALF_UP.
 */
public record ChargeableWeight(BigDecimal deadWeightKg, BigDecimal volumetricWeightKg, BigDecimal chargeableWeightKg) {

    public static final BigDecimal CM3_PER_CUBIC_FOOT = new BigDecimal("28316.846592");
    private static final int SCALE = 3;

    public static ChargeableWeight of(List<ShipmentPackage> packages, WeightRules rules) {
        BigDecimal dead = BigDecimal.ZERO;
        BigDecimal volumetric = BigDecimal.ZERO;
        BigDecimal perPieceSum = BigDecimal.ZERO;

        for (ShipmentPackage p : packages) {
            BigDecimal count = BigDecimal.valueOf(p.count());
            BigDecimal pieceVol = volumetricKg(p.volumeCm3(), rules);
            dead = dead.add(p.totalDeadWeightKg());
            volumetric = volumetric.add(volumetricKg(p.totalVolumeCm3(), rules));
            perPieceSum = perPieceSum.add(p.deadWeightKg().max(pieceVol).multiply(count));
        }

        BigDecimal billed = rules.mpsRule() == MpsRule.PER_PIECE ? perPieceSum : dead.max(volumetric);
        BigDecimal chargeable = billed.max(rules.minChargeableWeightKg());

        return new ChargeableWeight(scale(dead), scale(volumetric), scale(chargeable));
    }

    private static BigDecimal volumetricKg(BigDecimal volumeCm3, WeightRules rules) {
        if (rules.volumetricDivisor() != null) {
            return volumeCm3.divide(rules.volumetricDivisor(), SCALE, RoundingMode.HALF_UP);
        }
        if (rules.cftKg() != null) {
            return volumeCm3.multiply(rules.cftKg()).divide(CM3_PER_CUBIC_FOOT, SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
