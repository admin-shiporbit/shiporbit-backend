package com.shiporbit.backend.rate.routing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Delhivery B2C (express parcel) rate API: GET /api/kinko/v1/invoice/charges/.json.
 * Used by DelhiveryPartnerClient for shipments lighter than maxWeightKg; heavier ones keep the LTL flow.
 *
 * @param routerUrl          e.g. https://staging-express.delhivery.com (prod: https://track.delhivery.com - confirm with Delhivery)
 * @param apiToken           static API key; sent as "Authorization: Token <apiToken>"
 * @param defaultMode        md: S = Surface, E = Express. Used when the request does not ask for EXPRESS/AIR
 * @param shipmentStatus     ss: Delivered (forward), RTO, DTO
 * @param maxWeightKg        dead weight below this goes to B2C; at or above it goes to LTL
 * @param volumetricDivisor  cm^3 per kg, used to send chargeable weight (cgm) - the API takes no dimensions
 */
@ConfigurationProperties(prefix = "rate-aggregator.delhivery-b2c")
public record DelhiveryB2cConfigProperties(
        String routerUrl,
        String ratecalculatorEndpoint,
        String apiToken,
        String defaultMode,
        String shipmentStatus,
        Double maxWeightKg,
        Integer volumetricDivisor
) {
    public DelhiveryB2cConfigProperties {
        ratecalculatorEndpoint = blank(ratecalculatorEndpoint) ? "/api/kinko/v1/invoice/charges/.json" : ratecalculatorEndpoint;
        defaultMode = blank(defaultMode) ? "S" : defaultMode;
        shipmentStatus = blank(shipmentStatus) ? "Delivered" : shipmentStatus;
        maxWeightKg = maxWeightKg == null ? 20.0 : maxWeightKg;
        volumetricDivisor = volumetricDivisor == null ? 5000 : volumetricDivisor;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
