package com.shiporbit.backend.rate.delhivery;

import com.shiporbit.backend.dto.Dimension;
import com.shiporbit.backend.rate.DeliveryRateStrategy;
import com.shiporbit.backend.rate.RiskType;
import com.shiporbit.backend.rate.ShipmentRequest;
import com.shiporbit.backend.rate.dto.RateBreakdown;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DelhiveryRateStrategy implements DeliveryRateStrategy {

    private static final String PARTNER_CODE = "DELHIVERY";
    private static final String PARTNER_NAME = "Delhivery";

    private final DelhiveryRateSource rateSource;
    private final DelhiveryRateCardConfig config;

    public DelhiveryRateStrategy(DelhiveryRateSource rateSource, DelhiveryRateCardConfig config) {
        this.rateSource = rateSource;
        this.config = config;
    }

    @Override
    public String getPartnerCode() {
        return PARTNER_CODE;
    }

    @Override
    public String getPartnerName() {
        return PARTNER_NAME;
    }

    @Override
    public RateBreakdown calculateRate(ShipmentRequest req) {
        if (req.getSourceZone() == null || req.getDestZone() == null) {
            throw new IllegalArgumentException("sourceZone and destZone are required");
        }
        int numberOfLRs = Math.max(1, req.getNumberOfLRs());

        RateBreakdown b = new RateBreakdown();

        // 1. Weight: actual vs volumetric vs per-LR minimum
        b.actualWeightKg = req.getActualWeightKg();
        b.volumetricWeightKg = computeVolumetricWeight(req.getPackages(), config.volumetricDivisor);
        double chargeableWt = Math.max(b.actualWeightKg, b.volumetricWeightKg);
        chargeableWt = Math.max(chargeableWt, config.minChargeableWeightPerLRKg * numberOfLRs);
        b.chargeableWeightKg = chargeableWt;

        // 2. Base rate + freight (override table checked first, then zone matrix)
        b.ratePerKg = rateSource.getRate(req.getSourceZone(), req.getDestZone(), req.getDestStateOrCity());
        b.baseFreight = round2(chargeableWt * b.ratePerKg);

        // 3. Fuel surcharge
        b.fsc = round2(b.baseFreight * config.fscPercent / 100.0);

        // 4. Processing charge (flat per LR)
        b.processing = config.processingChargePerLR * numberOfLRs;

        // 5. Risk / insurance (ROV)
        b.rov = computeRov(req, numberOfLRs);

        // 6. Handling (weight-slab based)
        b.handling = computeSlabCharge(config.handlingSlabs, chargeableWt);

        // 7. ODA (out-of-delivery-area)
        b.oda = computeOda(req, chargeableWt);

        // 8. Conditional add-ons - only applied when flagged on the shipment
        if (req.isFloorDelivery()) {
            b.floorDeliveryCharge = Math.max(chargeableWt * config.floorDeliveryPerKg,
                    config.floorDeliveryMinPerLR * numberOfLRs);
        }
        if (req.isMallDelivery()) {
            b.mallDeliveryCharge = Math.max(chargeableWt * config.mallDeliveryPerKg,
                    config.mallDeliveryMinPerLR * numberOfLRs);
        }
        if (req.isCsdArmyDelivery()) {
            b.csdArmyCharge = Math.max(chargeableWt * config.csdArmyPerKg,
                    config.csdArmyMinPerLR * numberOfLRs);
        }
        if (req.isSundayOrHolidayDelivery()) {
            b.sundayHolidayCharge = config.sundayHolidayPerLR * numberOfLRs;
        }
        if (req.isToPay()) {
            b.toPayCharge = config.toPayPerLR * numberOfLRs;
        }
        if (req.isChequePayment()) {
            b.chequeHandlingCharge = config.chequeHandlingPerLR * numberOfLRs;
        }
        if (req.getCollectableCodAmount() != null) {
            b.cashHandlingCharge = Math.max(req.getCollectableCodAmount() * config.cashHandlingPercent / 100.0,
                    config.cashHandlingMinPerLR * numberOfLRs);
        }
        b.greenTax = Math.max(chargeableWt * config.greenTaxPerKg, config.greenTaxMinPerLR * numberOfLRs);

        // 9. Totals, floored by the minimum LR charge, then rounded
        double subtotal = b.baseFreight + b.fsc + b.processing + b.rov + b.handling + b.oda
                + b.floorDeliveryCharge + b.mallDeliveryCharge + b.csdArmyCharge
                + b.sundayHolidayCharge + b.toPayCharge + b.chequeHandlingCharge
                + b.cashHandlingCharge + b.greenTax;
        b.subtotal = round2(subtotal);

        double total = Math.max(b.subtotal, config.minLrCharge * numberOfLRs);
        b.total = config.roundOffTotal ? Math.round(total) : round2(total);

        return b;
    }

    private double computeVolumetricWeight(List<Dimension> packages, double divisor) {
        if (packages == null) return 0;
        double total = 0;
        for (Dimension d : packages) {
            double l = d.getLength() != null ? d.getLength() : 0;
            double w = d.getWidth() != null ? d.getWidth() : 0;
            double h = d.getHeight() != null ? d.getHeight() : 0;
            total += (l * w * h) / divisor;
        }
        return total;
    }

    private double computeRov(ShipmentRequest req, int numberOfLRs) {
        if (req.getRiskType() == RiskType.OWNER) {
            return Math.max(req.getDeclaredValue() * config.rovOwnerPercent / 100.0,
                    config.rovOwnerMinPerLR * numberOfLRs);
        }
        return Math.max(req.getDeclaredValue() * config.rovCarrierPercent / 100.0,
                config.rovCarrierMinPerLR * numberOfLRs);
    }

    private double computeSlabCharge(List<double[]> slabs, double weightKg) {
        for (double[] slab : slabs) {
            double min = slab[0], max = slab[1], rate = slab[2];
            if (weightKg >= min && weightKg < max) {
                return weightKg * rate;
            }
        }
        return 0;
    }

    private double computeOda(ShipmentRequest req, double weightKg) {
        if (!config.odaEnabled) {
            return 0;
        }

        boolean applyForDelivery = config.odaMode == 1 || config.odaMode == 3;
        boolean applyForPickup = config.odaMode == 2 || config.odaMode == 3;

        boolean deliveryExempt = req.getDeliveryPincode() != null
                && config.deliveryOdaExemptPincodes.contains(req.getDeliveryPincode());
        boolean pickupExempt = req.getPickupPincode() != null
                && config.pickupOdaExemptPincodes.contains(req.getPickupPincode());

        boolean odaApplies = (applyForDelivery && !deliveryExempt) || (applyForPickup && !pickupExempt);
        if (!odaApplies) return 0;

        for (double[] slab : config.odaSlabs) {
            double min = slab[0], max = slab[1], rate = slab[2], minCharge = slab[3];
            if (weightKg >= min && weightKg < max) {
                return Math.max(weightKg * rate, minCharge);
            }
        }
        return 0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
