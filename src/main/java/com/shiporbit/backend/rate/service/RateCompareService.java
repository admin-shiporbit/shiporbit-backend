package com.shiporbit.backend.rate.service;

// Imports: engine types (QuoteRequest / QuoteResult / ChargeableWeight), compare DTOs, partner profiles, loader, Flow logging.
import com.shiporbit.backend.rate.core.ChargeableWeight;
import com.shiporbit.backend.rate.core.QuoteRequest;
import com.shiporbit.backend.rate.core.QuoteResult;
import com.shiporbit.backend.rate.dto.request.CompareRatesRequest;
import com.shiporbit.backend.rate.dto.response.CompareRatesResponse;
import com.shiporbit.backend.rate.dto.response.CompareRatesResponse.CarrierResult;
import com.shiporbit.backend.rate.dto.response.CompareRatesResponse.Unavailable;
import com.shiporbit.backend.rate.engine.RateCardSnapshot;
import com.shiporbit.backend.rate.entity.DataStatus;
import com.shiporbit.backend.rate.entity.ServiceMode;
import com.shiporbit.backend.rate.routing.PartnerProfileProperties;
import com.shiporbit.backend.rate.routing.PartnerProfileProperties.Profile;
import com.shiporbit.backend.rate.service.RateCardLoader.LoadedService;
import com.shiporbit.backend.logging.Flow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * POST /api/v1/rates/compare: the frontend's request/response format on top of the same pricing as
 * /api/v1/rates/quotes (Adapter). Converts the request (CompareRatesMapper), prices every partner through
 * RateQuoteService.quoteAll (live APIs + rate cards), keeps the delivery mode asked for, sorts by fare.
 */
// Spring singleton service bean.
@Service
public class RateCompareService {

    // Logger for this class; Flow.step() writes through it with the dynamic step number (1, 2, 3a ...).
    private static final Logger LOGGER = LoggerFactory.getLogger(RateCompareService.class);

    // Prices every partner (live APIs + rate cards).
    private final RateQuoteService quoteService;
    // Reads ACTIVE rate cards from cache/DB (used here for partner names and card details shown in the carrier block).
    private final RateCardLoader loader;
    // Display data per partner from application.yaml: name, initials, colour, ETD, rating (shiporbit.partners.profiles).
    private final PartnerProfileProperties profiles;

    // Constructor injection of the three collaborators.
    public RateCompareService(RateQuoteService quoteService, RateCardLoader loader, PartnerProfileProperties profiles) {
        // Store quote service.
        this.quoteService = quoteService;
        // Store loader.
        this.loader = loader;
        // Store profiles.
        this.profiles = profiles;
    }

    // Called by RateCompareController.compare().
    public CompareRatesResponse compare(CompareRatesRequest request) {
        // Step 1a. "Surface" / "Air" / "Express" / "All" -> set of ServiceModes to keep. Unknown value -> IllegalArgumentException -> 400.
        Set<ServiceMode> modes = CompareRatesMapper.modesFor(request.deliveryMode());
        // Step 1b. Frontend strings + units -> engine QuoteRequest (kg, cm, PREPAID/COD, declared value, one package). See CompareRatesMapper.toQuoteRequest.
        QuoteRequest quote = CompareRatesMapper.toQuoteRequest(request);
        // Step 1c. Shipment-level dead / volumetric (divisor 5000) / chargeable weight - shown at the top of the response only; each partner bills with its own card rules.
        ChargeableWeight weights = CompareRatesMapper.displayWeights(quote);
        // Log step: what was received and how it was mapped.
        Flow.step(LOGGER, "Compare request mapped: {} -> {}, mode {} -> {}, weight {} {} -> chargeable {} kg, COD {}, order value {}",
                request.fromPin(), request.toPin(), request.deliveryMode(), modes, request.weight(), request.weightUnit(),
                weights.chargeableWeightKg(), request.cod(), request.orderValue());

        // Step 2. partnerId -> partner name (cached SO_DELIVERY_PARTNER_LIST).
        Map<Integer, String> partnerNames = loader.partnerNames();
        // "partnerId/serviceCode" -> rate card, to show slab base rate, per-kg, COD rule and max weight in the carrier block.
        Map<String, RateCardSnapshot> cards = new HashMap<>();          // cached; for COD rule + max weight
        // Every enabled service with the card valid on the pickup date (cached, see RateCardLoader.servicesOn).
        for (LoadedService s : loader.servicesOn(quote.pickupDate())) {
            // Live-API services have no card (card == null) - nothing to remember.
            if (s.card() != null) {
                // Remember the card under its key.
                cards.put(key(s.service().partnerId(), s.service().serviceCode()), s.card());
            }
        }

        // Priced results converted to the frontend shape.
        List<CarrierResult> results = new ArrayList<>();
        // Not-priced results (not serviceable, over weight, no zone ...) with the reason.
        List<Unavailable> unavailable = new ArrayList<>();
        // Becomes true if any priced result came from SAMPLE (not contract) data.
        boolean sample = false;
        // Step 3. Price ALL partners: RateQuoteService.quoteAll() (live APIs in parallel + every rate card). Loop over each result.
        for (QuoteResult r : quoteService.quoteAll(quote)) {
            // Result of another mode than asked (e.g. AIR when Surface was asked; HYPERLOCAL always)?
            if (!modes.contains(r.mode())) {
                // Skip it.
                continue;                                   // not the delivery mode asked for
            }
            // Display profile for this partner (falls back to the DB name when not configured).
            Profile profile = profiles.forPartner(r.partnerId(), partnerNames.get(r.partnerId()));
            // Java 21 pattern switch on the sealed QuoteResult (Priced | Unavailable).
            switch (r) {
                // Priced: has total and breakdown.
                case QuoteResult.Priced p -> {
                    // Add one CarrierResult made of:
                    results.add(new CarrierResult(
                            // carrier block (name, base rate and per-kg incl. margin, COD rule, ETD, rating, max weight) - card is null for live-API partners,
                            CompareRatesMapper.carrier(p, profile, cards.get(key(p.partnerId(), p.serviceCode()))),
                            // fare block (shipping, COD part, GST, total, billable weight, customer-facing breakdown lines).
                            CompareRatesMapper.fare(p, weights.chargeableWeightKg())));
                    // Response is "sample" if any price came from a SAMPLE card.
                    sample |= p.dataStatus() == DataStatus.SAMPLE;
                }
                // Unavailable: status, error code (SO-*) and reason.
                case QuoteResult.Unavailable u ->
                        // Add it to the list shown under the results.
                        unavailable.add(new Unavailable(profile.displayName(), u.serviceCode(), u.status(), u.errorCode(), u.reason()));
            }
        }
        // Step 4. Sort cheapest first; equal totals -> by carrier name (deterministic order).
        results.sort(Comparator.comparing((CarrierResult c) -> c.result().total()).thenComparing(c -> c.carrier().name()));
        // Log step: counts and the cheapest.
        Flow.step(LOGGER, "Compare response built: {} result(s) sorted by fare, {} unavailable, cheapest {}",
                results.size(), unavailable.size(),
                results.isEmpty() ? "-" : results.get(0).carrier().name() + " " + results.get(0).result().total());

        // Number of distinct carriers that returned a price.
        int partnerCount = (int) results.stream().map(c -> c.carrier().name()).distinct().count();
        // True only when every priced carrier has ETD configured (enables "sort by speed" in the UI).
        boolean etdForAll = !results.isEmpty() && results.stream().allMatch(c -> Objects.nonNull(c.carrier().etd()));

        // Step 5. Build the response: echo of the request, shipment-level weights, partner count, sort options, results, unavailable. Returned to the controller -> JSON 200.
        return new CompareRatesResponse(true, "INR", sample, request.deliveryMode(), request.fromPin(), request.toPin(),
                request.weight(), request.weightUnit(), request.length(), request.width(), request.height(),
                request.dimensionUnit(), request.cod(), request.orderValue(),
                CompareRatesMapper.plain(weights.deadWeightKg()), CompareRatesMapper.plain(weights.volumetricWeightKg()),
                CompareRatesMapper.plain(weights.chargeableWeightKg()), partnerCount, "fare",
                CompareRatesMapper.sortOptions(etdForAll), results, unavailable);
    }

    // Helper: key for the cards map.
    private static String key(Integer partnerId, String serviceCode) {
        // "partnerId/serviceCode", e.g. "14/B2B_SURFACE".
        return partnerId + "/" + serviceCode;
    }
}
