package com.shiporbit.backend.rate.dto.response;

import com.shiporbit.backend.rate.entity.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Read-only response records for the rate-card inspection API.
 * Entities are never returned from controllers; each view maps one entity.
 */
public final class RateCardViews {

    private RateCardViews() { }

    public record ServiceView(Long id, Integer partnerId, String serviceCode, String serviceName,
                              PricingModel pricingModel, ServiceMode mode) {
        public static ServiceView from(RateServiceEntity e) {
            return new ServiceView(e.getId(), e.getPartnerId(), e.getServiceCode(), e.getServiceName(),
                    e.getPricingModel(), e.getMode());
        }
    }

    public record SlabView(SlabDirection direction, String locationKey, RateUnit unit,
                           BigDecimal fromUnit, BigDecimal toUnit, BigDecimal firstUnit, BigDecimal firstRate,
                           BigDecimal addUnit, BigDecimal addRate, PaymentMode paymentMode,
                           ShipmentType shipmentType) {
        public static SlabView from(RateSlabEntity e) {
            return new SlabView(e.getDirection(), e.getLocationKey(), e.getUnit(), e.getFromUnit(), e.getToUnit(),
                    e.getFirstUnit(), e.getFirstRate(), e.getAddUnit(), e.getAddRate(),
                    e.getPaymentMode(), e.getShipmentType());
        }
    }

    public record RuleView(String chargeCode, Integer seq, CalcType calcType, BigDecimal rateValue,
                           BigDecimal fixedAmount, BigDecimal minAmount, BigDecimal maxAmount, ChargeBase base,
                           List<String> baseCodes, BigDecimal tierFrom, BigDecimal tierTo,
                           AppliesWhen appliesWhen, String appliesValue, Set<ShipmentDirection> directions,
                           LocalDate validFrom, LocalDate validTo) {
        public static RuleView from(RateChargeRuleEntity e) {
            return new RuleView(e.getChargeCode(), e.getSeq(), e.getCalcType(), e.getRateValue(),
                    e.getFixedAmount(), e.getMinAmount(), e.getMaxAmount(), e.getBase(), e.getBaseCodes(),
                    e.getTierFrom(), e.getTierTo(), e.getAppliesWhen(), e.getAppliesValue(), e.getDirections(),
                    e.getValidFrom(), e.getValidTo());
        }
    }

    public record CardView(Long id, Long serviceId, String versionLabel, CardStatus status, DataStatus dataStatus,
                           LocalDate effectiveFrom, LocalDate effectiveTo, BigDecimal volumetricDivisor,
                           BigDecimal cftKg, BigDecimal minChargeableWeightKg, BigDecimal maxWeightKg,
                           BigDecimal maxDistanceKm, MpsRule mpsRule, RtoRule rtoRule, BigDecimal rtoPercent,
                           BigDecimal minBill, boolean ratesIncludeGst, Rounding rounding,
                           List<SlabView> slabs, List<RuleView> rules) {
        public static CardView from(RateCardEntity c, List<SlabView> slabs, List<RuleView> rules) {
            return new CardView(c.getId(), c.getServiceId(), c.getVersionLabel(), c.getStatus(), c.getDataStatus(),
                    c.getEffectiveFrom(), c.getEffectiveTo(), c.getVolumetricDivisor(), c.getCftKg(),
                    c.getMinChargeableWeightKg(), c.getMaxWeightKg(), c.getMaxDistanceKm(), c.getMpsRule(),
                    c.getRtoRule(), c.getRtoPercent(), c.getMinBill(), c.isRatesIncludeGst(), c.getRounding(),
                    slabs, rules);
        }
    }

    /** Serviceability flags of one partner at one pincode. */
    public record PincodeView(String pincode, String cityCode, Integer stateId, String stateName,
                              boolean pickup, boolean delivery, boolean cod, boolean prepaid,
                              boolean reversePickup, boolean oda, boolean specialCity) {
        public static PincodeView from(PartnerPincodeEntity e, String stateName) {
            return new PincodeView(e.getId().getPincode(), e.getCityCode(), e.getStateId(), stateName,
                    e.isPickup(), e.isDelivery(), e.isCod(), e.isPrepaid(),
                    e.isReversePickup(), e.isOda(), e.isSpecialCity());
        }
    }

    public record ZoneView(PricingModel pricingModel, String locationKey) {
        public static ZoneView from(ZoneMapEntity e) {
            return new ZoneView(e.getId().getPricingModel(), e.getLocationKey());
        }
    }

    /** One partner on a lane. origin/destination null = partner has no row for that pincode. */
    public record PartnerLaneView(Integer partnerId, String partnerName,
                                  PincodeView origin, PincodeView destination, List<ZoneView> zones) { }

    public record LaneView(String origin, String destination, List<PartnerLaneView> partners) { }
}
