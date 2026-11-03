package com.shiporbit.backend.rate.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.shiporbit.backend.rate.core.QuoteStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response of POST /api/v1/rates/compare, in the frontend's format (sample_json/rate_calculator/response.json).
 * Additions to that sample: carrier.service, result.gst and the "unavailable" list.
 * Money is in INR including ShipOrbit margin and GST: result.shipping + result.cod = result.total.
 */
public record CompareRatesResponse(
        boolean success,
        String currency,
        @JsonProperty("isSample") boolean sample,       // true if any shown price came from a SAMPLE rate card
        String deliveryMode,
        String fromPin,
        String toPin,
        String weight,
        String weightUnit,
        String length,
        String width,
        String height,
        String dimensionUnit,
        @JsonProperty("isCOD") boolean cod,
        String orderValue,
        BigDecimal actualWeight,                        // kg
        BigDecimal volumetricWeight,                    // kg, L x W x H / 5000 (partners may use another divisor)
        BigDecimal chargeableWeight,                    // kg, max of the two; each result's billable is exact
        int availablePartnerCount,                      // partners with at least one price
        String sortBy,
        List<SortOption> sortOptions,
        List<CarrierResult> results,                    // cheapest first
        List<Unavailable> unavailable                   // services that could not price this shipment, with why
) {
    public CompareRatesResponse {
        sortOptions = List.copyOf(sortOptions);
        results = List.copyOf(results);
        unavailable = List.copyOf(unavailable);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SortOption(String key, String label, String field, String order, boolean enabled, String reason) { }

    public record CarrierResult(Carrier carrier, Fare result) { }

    /**
     * baseRate / perKg / codFlat / codPct: the matched rate-card slab and COD rule with ShipOrbit margin
     * (before GST); null where the partner prices through its own API or the card has no such rate.
     * etd: [min, max] days; rating: 0-5. Both from shiporbit.partners.profiles, null until configured.
     */
    public record Carrier(String name, String service, String initials, String color,
                          BigDecimal baseRate, BigDecimal perKg, BigDecimal codFlat, BigDecimal codPct,
                          List<Integer> etd, BigDecimal rating, BigDecimal maxKg) { }

    /** shipping + cod = total; gst is included in total. billable = chargeable kg used by this partner. */
    public record Fare(BigDecimal shipping, BigDecimal cod, BigDecimal gst, BigDecimal total,
                       BigDecimal billable, BigDecimal distanceKm) { }

    /** errorCode: ErrorCode.code(), e.g. SO-SVC-001 (see com.shiporbit.backend.error.ErrorCode). */
    public record Unavailable(String name, String service, QuoteStatus status, String errorCode, String reason) { }
}
