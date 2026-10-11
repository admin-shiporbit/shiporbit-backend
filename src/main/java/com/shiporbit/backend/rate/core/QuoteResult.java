package com.shiporbit.backend.rate.core;

import com.shiporbit.backend.rate.entity.DataStatus;
import com.shiporbit.backend.rate.entity.ServiceMode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Result of quoting one partner service: either a price or a reason it could not be priced.
 * Sealed so callers handle both cases with an exhaustive switch.
 */
public sealed interface QuoteResult permits QuoteResult.Priced, QuoteResult.Unavailable {

    Integer partnerId();

    String partnerName();

    String serviceCode();

    ServiceMode mode();

    DataStatus dataStatus();        // SAMPLE / CONTRACT for rate cards; null for live partner APIs

    QuoteStatus status();

    record Priced(
            Integer partnerId,
            String partnerName,
            String serviceCode,
            ServiceMode mode,
            DataStatus dataStatus,
            String rateCardVersion,
            String locationKey,             // zone / band / zone pair / vehicle
            BigDecimal volumetricWeightKg,
            BigDecimal chargeableWeightKg,  // kg; hyperlocal prices by km (see slab.unit)
            SlabInfo slab,
            List<ChargeLine> breakdown,     // BASE + charges + MARGIN + GST, in order
            BigDecimal taxableAmount,
            BigDecimal gst,
            BigDecimal total
    ) implements QuoteResult {
        public Priced {
            breakdown = List.copyOf(Objects.requireNonNull(breakdown, "breakdown"));
        }

        @Override
        public QuoteStatus status() {
            return QuoteStatus.OK;
        }
    }

    record Unavailable(
            Integer partnerId,
            String partnerName,
            String serviceCode,
            ServiceMode mode,
            DataStatus dataStatus,
            QuoteStatus status,
            String reason
    ) implements QuoteResult {
        public Unavailable {
            Objects.requireNonNull(status, "status");
            if (status == QuoteStatus.OK) {
                throw new IllegalArgumentException("Unavailable result cannot have status OK");
            }
        }
    }
}
