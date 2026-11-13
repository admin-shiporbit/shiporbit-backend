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
// Dead, volumetric and chargeable weight (kg).
public record ChargeableWeight(BigDecimal deadWeightKg, BigDecimal volumetricWeightKg, BigDecimal chargeableWeightKg) {

    // cm3 in one cubic foot (B2B CFT).
    public static final BigDecimal CM3_PER_CUBIC_FOOT = new BigDecimal("28316.846592");
    // 3 decimals.
    private static final int SCALE = 3;

    // Compute weights for packages under a card's rules.
    public static ChargeableWeight of(List<ShipmentPackage> packages, WeightRules rules) {
        // Total dead weight.
        BigDecimal dead = BigDecimal.ZERO;
        // Total volumetric weight.
        BigDecimal volumetric = BigDecimal.ZERO;
        // Sum of max(dead, volumetric) per piece (MPS PER_PIECE).
        BigDecimal perPieceSum = BigDecimal.ZERO;

        // For each package line:
        for (ShipmentPackage p : packages) {
            // piece count,
            BigDecimal count = BigDecimal.valueOf(p.count());
            // volumetric of one piece,
            BigDecimal pieceVol = volumetricKg(p.volumeCm3(), rules);
            // add dead weight x count,
            dead = dead.add(p.totalDeadWeightKg());
            // add volumetric x count,
            volumetric = volumetric.add(volumetricKg(p.totalVolumeCm3(), rules));
            // add max(piece dead, piece volumetric) x count.
            perPieceSum = perPieceSum.add(p.deadWeightKg().max(pieceVol).multiply(count));
        }

        // PER_PIECE -> per-piece sum; TOTAL -> max(total dead, total volumetric).
        BigDecimal billed = rules.mpsRule() == MpsRule.PER_PIECE ? perPieceSum : dead.max(volumetric);
        // Apply the card minimum (e.g. 20 kg XpressBees).
        BigDecimal chargeable = billed.max(rules.minChargeableWeightKg());

        // Round to 3 dp and return.
        return new ChargeableWeight(scale(dead), scale(volumetric), scale(chargeable));
    }

    // Volumetric kg from cm3.
    private static BigDecimal volumetricKg(BigDecimal volumeCm3, WeightRules rules) {
        // Courier: divisor set?
        if (rules.volumetricDivisor() != null) {
            // cm3 / divisor (e.g. 4500 / 5000).
            return volumeCm3.divide(rules.volumetricDivisor(), SCALE, RoundingMode.HALF_UP);
        }
        // B2B: CFT set?
        if (rules.cftKg() != null) {
            // cm3 x CFT kg / 28,316.846592.
            return volumeCm3.multiply(rules.cftKg()).divide(CM3_PER_CUBIC_FOOT, SCALE, RoundingMode.HALF_UP);
        }
        // Neither -> 0.
        return BigDecimal.ZERO;
    }

    // 3 dp, HALF_UP.
    private static BigDecimal scale(BigDecimal v) {
        // Round.
        return v.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
