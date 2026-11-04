package com.shiporbit.backend.rate.dto.response;

import com.shiporbit.backend.rate.core.QuoteRequest;
import com.shiporbit.backend.rate.entity.PricingModel;
import com.shiporbit.backend.rate.entity.ShipmentType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response of POST /api/v1/rates/quotes/preview: the validated request with defaults applied,
 * and chargeable weight per enabled service. No prices yet (step 4).
 */
public record QuotePreview(
        QuoteRequest request,
        ShipmentType shipmentType,
        int totalPieces,
        List<ServiceWeight> services
) {
    public QuotePreview {
        services = List.copyOf(services);
    }

    /** Weight figures are null when the service has no ACTIVE card on the pickup date. */
    public record ServiceWeight(
            Integer partnerId,
            String partnerName,
            String serviceCode,
            PricingModel pricingModel,
            String rateCardVersion,
            BigDecimal deadWeightKg,
            BigDecimal volumetricWeightKg,
            BigDecimal chargeableWeightKg,
            BigDecimal maxWeightKg,
            Boolean withinWeightLimit,
            String note
    ) { }
}
