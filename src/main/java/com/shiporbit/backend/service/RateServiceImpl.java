package com.shiporbit.backend.service;

import com.shiporbit.backend.dto.DeliveryPartnerResponse;
import com.shiporbit.backend.dto.RateQuoteRequest;
import com.shiporbit.backend.dto.RateQuoteResponse;
import com.shiporbit.backend.rate.RateCalculator;
import com.shiporbit.backend.rate.RiskType;
import com.shiporbit.backend.rate.ShipmentRequest;
import com.shiporbit.backend.rate.Zones;
import com.shiporbit.backend.rate.dto.RateBreakdown;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RateServiceImpl implements RateService {

    private final RateCalculator rateCalculator;
    private final Map<String, String> partnerNamesByCode;

    public RateServiceImpl(RateCalculator rateCalculator) {
        this.rateCalculator = rateCalculator;
        this.partnerNamesByCode = rateCalculator.getAvailablePartners().stream()
                .collect(Collectors.toMap(
                        RateCalculator.PartnerInfo::code,
                        RateCalculator.PartnerInfo::name));
    }

    @Override
    public List<DeliveryPartnerResponse> availablePartners() {
        return rateCalculator.getAvailablePartners().stream()
                .map(p -> new DeliveryPartnerResponse(p.code(), p.name()))
                .toList();
    }

    @Override
    public RateQuoteResponse quote(String partnerCode, RateQuoteRequest request) {
        ShipmentRequest shipmentRequest = toShipmentRequest(request);
        RateBreakdown breakdown = rateCalculator.calculate(partnerCode, shipmentRequest);
        return new RateQuoteResponse(partnerCode, partnerNameOf(partnerCode), breakdown);
    }

    @Override
    public List<RateQuoteResponse> compareAll(RateQuoteRequest request) {
        ShipmentRequest shipmentRequest = toShipmentRequest(request);
        return rateCalculator.calculateForAllServiceablePartners(shipmentRequest).entrySet().stream()
                .map(entry -> new RateQuoteResponse(entry.getKey(), partnerNameOf(entry.getKey()), entry.getValue()))
                .toList();
    }

    private String partnerNameOf(String partnerCode) {
        return partnerNamesByCode.getOrDefault(partnerCode, partnerCode);
    }

    private ShipmentRequest toShipmentRequest(RateQuoteRequest request) {
        RiskType riskType = RiskType.CARRIER;
        if (request.riskType() != null && !request.riskType().isBlank()) {
            riskType = RiskType.valueOf(request.riskType().trim().toUpperCase(Locale.ROOT));
        }

        return ShipmentRequest.builder()
                .sourceZone(Zones.fromState(request.sourceState()))
                .destZone(Zones.fromState(request.destState()))
                .sourceStateOrCity(request.sourceState())
                .destStateOrCity(request.destState())
                .pickupPincode(request.pickupPincode())
                .deliveryPincode(request.deliveryPincode())
                .actualWeightKg(request.actualWeightKg())
                .packages(request.packages() != null ? request.packages() : List.of())
                .declaredValue(request.declaredValue())
                .riskType(riskType)
                .numberOfLRs(request.numberOfLRs() != null ? request.numberOfLRs() : 1)
                .floorDelivery(Boolean.TRUE.equals(request.floorDelivery()))
                .mallDelivery(Boolean.TRUE.equals(request.mallDelivery()))
                .csdArmyDelivery(Boolean.TRUE.equals(request.csdArmyDelivery()))
                .sundayOrHolidayDelivery(Boolean.TRUE.equals(request.sundayOrHolidayDelivery()))
                .toPay(Boolean.TRUE.equals(request.toPay()))
                .chequePayment(Boolean.TRUE.equals(request.chequePayment()))
                .collectableCodAmount(request.collectableCodAmount())
                .build();
    }
}
