package com.shiporbit.backend.rate;

import com.shiporbit.backend.dto.Dimension;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Carrier-agnostic shipment input. Every {@link DeliveryRateStrategy} calculates its
 * rate from the same request shape, so a new delivery partner never needs a new
 * request type - only a new RateSource + RateCardConfig + DeliveryRateStrategy.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentRequest {

    private Zones sourceZone;
    private Zones destZone;
    private String sourceStateOrCity;   // used for GST place-of-supply (intra vs inter-state)
    private String destStateOrCity;     // used for rate-override lookup AND GST place-of-supply
    private String pickupPincode;
    private String deliveryPincode;

    private double actualWeightKg;

    @Builder.Default
    private List<Dimension> packages = new ArrayList<>();

    private double declaredValue;

    @Builder.Default
    private RiskType riskType = RiskType.CARRIER;

    @Builder.Default
    private int numberOfLRs = 1;

    private boolean floorDelivery;
    private boolean mallDelivery;
    private boolean csdArmyDelivery;
    private boolean sundayOrHolidayDelivery;
    private boolean toPay;
    private boolean chequePayment;
    private Double collectableCodAmount; // set only when cash handling applies (COD)
}
