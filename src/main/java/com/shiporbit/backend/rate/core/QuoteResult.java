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
// Result of pricing one partner service: Priced or Unavailable (sealed, so switches are exhaustive).
public sealed interface QuoteResult permits QuoteResult.Priced, QuoteResult.Unavailable {

    // Partner id.
    Integer partnerId();

    // Partner name.
    String partnerName();

    // Service code (e.g. B2B_SURFACE, LIVE_API).
    String serviceCode();

    // SURFACE / AIR / SURFACE_AIR / HYPERLOCAL (used for the deliveryMode filter).
    ServiceMode mode();

    DataStatus dataStatus();        // SAMPLE / CONTRACT for rate cards; null for live partner APIs

    // OK or a failure status.
    QuoteStatus status();

    // A price.
    record Priced(
            // partner id,
            Integer partnerId,
            // partner name,
            String partnerName,
            // service code,
            String serviceCode,
            // mode,
            ServiceMode mode,
            // SAMPLE / CONTRACT,
            DataStatus dataStatus,
            // card version,
            String rateCardVersion,
            String locationKey,             // zone / band / zone pair / vehicle
            // volumetric kg,
            BigDecimal volumetricWeightKg,
            BigDecimal chargeableWeightKg,  // kg; hyperlocal prices by km (see slab.unit)
            // slab used,
            SlabInfo slab,
            List<ChargeLine> breakdown,     // BASE + charges + MARGIN + GST, in order
            // subtotal + margin,
            BigDecimal taxableAmount,
            // GST,
            BigDecimal gst,
            // final total.
            BigDecimal total
    ) implements QuoteResult {
        // Compact constructor:
        public Priced {
            // breakdown must exist; immutable copy.
            breakdown = List.copyOf(Objects.requireNonNull(breakdown, "breakdown"));
        }

        @Override
        // Priced is always
        public QuoteStatus status() {
            // OK.
            return QuoteStatus.OK;
        }
    }

    // No price.
    record Unavailable(
            // partner id,
            Integer partnerId,
            // partner name,
            String partnerName,
            // service code,
            String serviceCode,
            // mode,
            ServiceMode mode,
            // data status,
            DataStatus dataStatus,
            // why (NOT_SERVICEABLE, ZONE_UNKNOWN ...),
            QuoteStatus status,
            // reason text,
            String reason,
            String errorCode                // ErrorCode.code(), e.g. SO-SVC-001; defaults from status
    ) implements QuoteResult {
        // Compact constructor:
        public Unavailable {
            // status required,
            Objects.requireNonNull(status, "status");
            // cannot be OK,
            if (status == QuoteStatus.OK) {
                // -> otherwise IllegalArgumentException.
                throw new IllegalArgumentException("Unavailable result cannot have status OK");
            }
            // No code given?
            if (errorCode == null) {
                // Derive it from the status (ErrorCode.forQuoteStatus, e.g. SO-SVC-001).
                errorCode = com.shiporbit.backend.error.ErrorCode.forQuoteStatus(status).code();
            }
        }

        /** Error code derived from status (rate-card calculators). */
        // Shorter constructor
        public Unavailable(Integer partnerId, String partnerName, String serviceCode, ServiceMode mode,
                           DataStatus dataStatus, QuoteStatus status, String reason) {
            // with code = null (derived above).
            this(partnerId, partnerName, serviceCode, mode, dataStatus, status, reason, null);
        }
    }
}
