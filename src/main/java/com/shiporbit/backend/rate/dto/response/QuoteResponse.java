package com.shiporbit.backend.rate.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shiporbit.backend.rate.core.ChargeLine;
import com.shiporbit.backend.rate.core.QuoteStatus;
import com.shiporbit.backend.rate.core.SlabInfo;
import com.shiporbit.backend.rate.entity.DataStatus;
import com.shiporbit.backend.rate.entity.ServiceMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Response of POST /api/v1/rates/quotes. Shape follows docs/samples/quote_response_*.json:
 * results per partner -> services, each either priced (breakdown, totals) or with status + reason.
 */
public record QuoteResponse(
        String quoteId,
        String currency,
        LocalDate pickupDate,
        DataStatus dataStatus,          // SAMPLE if any result came from a sample card
        Cheapest cheapest,              // null when nothing could be priced
        List<PartnerResult> results
) {
    public QuoteResponse {
        results = List.copyOf(results);
    }

    public record Cheapest(Integer partnerId, String partner, String service, BigDecimal total) { }

    public record PartnerResult(Integer partnerId, String partner, List<ServiceQuote> services) {
        public PartnerResult {
            services = List.copyOf(services);
        }
    }

    /** Priced: status OK + figures. Not priced: status + reason only (nulls omitted from JSON). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ServiceQuote(
            String service,
            ServiceMode mode,
            DataStatus dataStatus,
            QuoteStatus status,
            String reason,
            String rateCardVersion,
            String zone,
            BigDecimal volumetricWeightKg,
            BigDecimal chargeableWeightKg,
            SlabInfo slab,
            List<ChargeLine> breakdown,     // zero-amount charges hidden; BASE always shown
            BigDecimal taxableAmount,
            BigDecimal gst,
            BigDecimal total,
            Boolean cheapestForPartner
    ) { }
}
