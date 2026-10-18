package com.shiporbit.backend.rate.routing;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @param serviceabilityEcommEndpoint      POST, Bearer id_token + tenantid (same as rate API). Body: fromPincode, toPincode, paymentMode, operationType, carriers
 * @param serviceabilityHyperlocalEndpoint POST, Bearer id_token + tenantid. Body: pickupAddress/shippingAddress {address, pincode, latitude, longitude}, ...
 * @param serviceabilityCarriers           carriers sent in serviceability calls (default [SMILE])
 * @param serviceabilityOperationType      default PICKUP_DELIVERY
 */
@ConfigurationProperties(prefix = "rate-aggregator.shreemurti")
public record ShreeMurtiConfigProperties(
        String userName,
        String password,
        String routerUrl,
        String loginEndPoint,
        String refreshTokenEndPoint,
        String rateCalculatorEndpoint,
        String signinType,
        String userId,
        String tenantId,
        String apiKey,
        String serviceabilityEcommEndpoint,
        String serviceabilityHyperlocalEndpoint,
        List<String> serviceabilityCarriers,
        String serviceabilityOperationType
) {
    public ShreeMurtiConfigProperties {
        serviceabilityEcommEndpoint = blank(serviceabilityEcommEndpoint) ? "/gateway/serviceability/ecomm" : serviceabilityEcommEndpoint;
        serviceabilityHyperlocalEndpoint = blank(serviceabilityHyperlocalEndpoint) ? "/gateway/serviceability/hyperlocal" : serviceabilityHyperlocalEndpoint;
        serviceabilityCarriers = serviceabilityCarriers == null || serviceabilityCarriers.isEmpty() ? List.of("SMILE") : List.copyOf(serviceabilityCarriers);
        serviceabilityOperationType = blank(serviceabilityOperationType) ? "PICKUP_DELIVERY" : serviceabilityOperationType;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
