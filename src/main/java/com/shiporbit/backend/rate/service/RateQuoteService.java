package com.shiporbit.backend.rate.service;

// Imports: engine records, calculator registry, live-API provider, partner allowlist, loader, Flow logging.
import com.shiporbit.backend.rate.core.ChargeLine;
import com.shiporbit.backend.rate.core.QuoteRequest;
import com.shiporbit.backend.rate.core.QuoteResult;
import com.shiporbit.backend.rate.core.QuoteStatus;
import com.shiporbit.backend.rate.dto.response.QuoteResponse;
import com.shiporbit.backend.rate.dto.response.QuoteResponse.Cheapest;
import com.shiporbit.backend.rate.dto.response.QuoteResponse.PartnerResult;
import com.shiporbit.backend.rate.dto.response.QuoteResponse.ServiceQuote;
import com.shiporbit.backend.rate.engine.CalculatorRegistry;
import com.shiporbit.backend.rate.engine.PartnerLane;
import com.shiporbit.backend.rate.engine.ServiceInfo;
import com.shiporbit.backend.rate.entity.DataStatus;
import com.shiporbit.backend.rate.provider.LivePartnerQuoteProvider;
import com.shiporbit.backend.rate.routing.PartnerAllowlistProperties;
import com.shiporbit.backend.rate.service.RateCardLoader.LoadedService;
import com.shiporbit.backend.logging.Flow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Prices one request against every partner (optionally filtered by partner name):
 * <ul>
 *   <li>Partners with a live rate API (LivePartnerQuoteProvider: Delhivery, Shree Maruti) are quoted
 *       through that API, in parallel, and their rate-card services are skipped.</li>
 *   <li>Every other partner is priced from its ACTIVE rate card (calculator per pricing model).</li>
 * </ul>
 * Results are merged, grouped by partner (partner id order) and the cheapest is marked.
 */
// Spring singleton service bean.
@Service
public class RateQuoteService {

    // Logger for this class.
    private static final Logger LOGGER = LoggerFactory.getLogger(RateQuoteService.class);
    // yyyyMMdd, used in the quote id.
    private static final DateTimeFormatter QUOTE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    // Rate cards, lanes and partner names (cached).
    private final RateCardLoader loader;
    // Pricing model -> calculator (ZONE_SLAB / B2B_FREIGHT / DISTANCE_BAND -> ZoneMapCalculator, HYPERLOCAL -> HyperlocalCalculator).
    private final CalculatorRegistry registry;
    // Partners priced through their own API (Delhivery, Shree Maruti).
    private final LivePartnerQuoteProvider liveProvider;
    // shiporbit.partners.enabled / disabled from application.yaml.
    private final PartnerAllowlistProperties allowlist;
    // Makes the "unknown partner name in config" warning run only once.
    private final AtomicBoolean allowlistChecked = new AtomicBoolean();

    // Constructor injection.
    public RateQuoteService(RateCardLoader loader, CalculatorRegistry registry, LivePartnerQuoteProvider liveProvider,
                            PartnerAllowlistProperties allowlist) {
        // Store loader.
        this.loader = loader;
        // Store registry.
        this.registry = registry;
        // Store live provider.
        this.liveProvider = liveProvider;
        // Store allowlist.
        this.allowlist = allowlist;
        // At startup: is shiporbit.partners.enabled set?
        if (allowlist.configured()) {
            // Log which partners are ON and OFF.
            LOGGER.info("Partners ON for quotes: {}; OFF: {} (shiporbit.partners.enabled / disabled)",
                    allowlist.names(true), allowlist.names(false));
            // A name in both lists?
            if (!allowlist.inBoth().isEmpty()) {
                // Warn (it is treated as disabled).
                LOGGER.warn("In both shiporbit.partners.enabled and disabled (treated as disabled): {}", allowlist.inBoth());
            }
        // enabled not set:
        } else {
            // warn that every partner not in disabled will be quoted.
            LOGGER.warn("shiporbit.partners.enabled is not set: every partner not in disabled {} will be quoted",
                    allowlist.names(false));
        }
    }

    // Used by /api/v1/rates/quotes (not by /compare).
    public QuoteResponse quote(QuoteRequest request) {
        // Price all, then group by partner.
        return toResponse(request, quoteAll(request));
    }

    /**
     * Every partner service's result for the request (priced or not), sorted by partner id then service code.
     * Used by quote() and by RateCompareService (frontend compare format).
     */
    // Step 3 (called from RateCompareService). Every partner service's result for the request.
    public List<QuoteResult> quoteAll(QuoteRequest request) {
        // Open a Flow scope: logs "Quote: ..." and nests the step numbers below under it (3a, 3b ...). Closed automatically at the end of try.
        try (Flow.Scope quoteScope = Flow.begin(LOGGER, "Quote: {} -> {}, {} package(s), {} kg dead weight, {}, declared {}, pickup {}",
                request.originPincode(), request.destinationPincode(), request.packages().size(),
                request.packages().stream().map(p -> p.totalDeadWeightKg()).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add),
                request.paymentMode(), request.declaredValue(), request.pickupDate())) {

            // Step 3a. partnerId -> name (cached).
            Map<Integer, String> partnerNames = loader.partnerNames();
            // If the request named partners (QuoteRequest.partners), they must exist, else 400. /compare sends none = all partners.
            PartnerFilter.requireKnown(request.partners(), partnerNames.values());
            // Once per app run: warn about names in enabled/disabled that are not in the DB.
            warnUnknownEnabledNamesOnce(partnerNames.values());
            // Partner ids handled by a live API (their rate cards, if any, are skipped).
            Set<Integer> livePartners = liveProvider.livePartnerIds();

            // Step 3b. Rate-card services valid on the pickup date (ACTIVE card, enabled service), then keep only those
            List<LoadedService> services = loader.servicesOn(request.pickupDate()).stream()
                    // not priced by a live API,
                    .filter(s -> !livePartners.contains(s.service().partnerId()))
                    // with a partner name, and asked for in the request (all when none named),
                    .filter(s -> s.service().partnerName() != null && request.includesPartner(s.service().partnerName()))
                    // and allowed by shiporbit.partners.enabled / disabled.
                    .filter(s -> allowlist.allows(s.service().partnerName()))      // shiporbit.partners.enabled
                    .toList();
            // Log the selected rate-card services.
            Flow.step(LOGGER, "Rate-card services selected: {} ({})", services.size(),
                    services.stream().map(s -> s.service().partnerName() + "/" + s.service().serviceCode()).toList());

            // Step 3c. Lane = per partner: origin + destination pincode rows (serviceability flags, city, state) and zone / location key per pricing model.
            Map<Integer, PartnerLane> lanes = services.isEmpty() ? Map.of()
                    // Loaded only if at least one rate-card service is left (cached per pincode pair).
                    : loader.lanes(request.originPincode(), request.destinationPincode());
            // Log how many partners have lane data.
            Flow.step(LOGGER, "Lane loaded: pincode serviceability + zones for {} partner(s)", lanes.size());

            // Step 3d. Live APIs first: LivePartnerQuoteProvider.quote() checks serviceability, then rates, in parallel (virtual threads), each with a timeout. Only allowed partners are called.
            List<QuoteResult> results = new ArrayList<>(liveProvider.quote(request, partnerNames, allowlist::allows));

            // Rate-card results.
            List<QuoteResult> cardResults;
            // Step 3e. Open a nested Flow scope for rate-card pricing.
            try (Flow.Scope cards = Flow.begin(LOGGER, "Rate-card pricing: {} service(s)", services.size())) {
                // For each selected service:
                cardResults = services.stream()
                        // price it with that partner's lane (EMPTY when the partner has no pincode rows -> not serviceable),
                        .map(s -> price(s, request, lanes.getOrDefault(s.service().partnerId(), PartnerLane.EMPTY)))
                        // log each result,
                        .peek(RateQuoteService::logCardResult)
                        // collect.
                        .toList();
            }
            // Merge live + card results.
            results.addAll(cardResults);
            // Sort by partner id, then service code.
            results.sort(Comparator.comparing(QuoteResult::partnerId).thenComparing(QuoteResult::serviceCode));
            // Count priced results.
            long priced = results.stream().filter(r -> r instanceof QuoteResult.Priced).count();
            // Log the summary.
            Flow.step(LOGGER, "Quote done: {} priced, {} unavailable", priced, results.size() - priced);
            // Back to RateCompareService (filter by mode, map, sort by fare).
            return results;
        }
    }

    // Log one rate-card result.
    private static void logCardResult(QuoteResult r) {
        // Priced or unavailable?
        switch (r) {
            // Priced: partner/service, total, zone, chargeable kg, card version.
            case QuoteResult.Priced p -> Flow.step(LOGGER, "{}/{}: priced {} (zone {}, chargeable {} kg, card {})",
                    p.partnerName(), p.serviceCode(), p.total(), p.locationKey(), p.chargeableWeightKg(), p.rateCardVersion());
            // Unavailable: partner/service, status, error code, reason.
            case QuoteResult.Unavailable u -> Flow.step(LOGGER, "{}/{}: {} [{}] {}",
                    u.partnerName(), u.serviceCode(), u.status(), u.errorCode(), u.reason());
        }
    }

    /** A typo in shiporbit.partners.enabled silently disables a partner, so say so once (names come from the DB). */
    // Warn once about allowlist typos.
    private void warnUnknownEnabledNamesOnce(java.util.Collection<String> knownNames) {
        // No allowlist, or already checked
        if (!allowlist.configured() || !allowlistChecked.compareAndSet(false, true)) {
            // -> do nothing.
            return;
        }
        // Names in config that match no DB partner
        List<String> unknown = allowlist.allNames().stream()
                // (comparison ignores case, spaces, _ and -).
                .filter(name -> knownNames.stream().noneMatch(known -> PartnerAllowlistProperties.sameName(name, known)))
                .toList();
        // Any?
        if (!unknown.isEmpty()) {
            // Warn.
            LOGGER.warn("shiporbit.partners.enabled/disabled have names not in SO_DELIVERY_PARTNER_LIST (ignored): {}", unknown);
        }
        // DB partners in neither list
        List<String> unlisted = knownNames.stream()
                // (these are not quoted),
                .filter(known -> allowlist.allNames().stream().noneMatch(name -> PartnerAllowlistProperties.sameName(name, known)))
                // sorted
                .sorted()
                // into a list.
                .toList();
        // Any?
        if (!unlisted.isEmpty()) {
            // Warn.
            LOGGER.warn("Partners in neither shiporbit.partners.enabled nor disabled (not quoted): {}", unlisted);
        }
    }

    // Price one rate-card service.
    private QuoteResult price(LoadedService loaded, QuoteRequest request, PartnerLane lane) {
        // Service identity.
        ServiceInfo s = loaded.service();
        // No card valid on the pickup date?
        if (loaded.card() == null) {
            // -> Unavailable RATE_UNAVAILABLE (SO-RATE code).
            return new QuoteResult.Unavailable(s.partnerId(), s.partnerName(), s.serviceCode(), s.mode(), null,
                    QuoteStatus.RATE_UNAVAILABLE, "No ACTIVE rate card on " + request.pickupDate());
        }
        // Find the calculator for this pricing model,
        return registry.forModel(s.pricingModel())
                // run its quote() (AbstractRateCardCalculator.quote - template method: rules -> zone -> slab -> charges -> margin + GST),
                .map(calculator -> calculator.quote(request, loaded.card(), lane))
                // or, when no calculator supports the model,
                .orElseGet(() -> new QuoteResult.Unavailable(s.partnerId(), s.partnerName(), s.serviceCode(), s.mode(),
                        // -> Unavailable CONFIG_INCOMPLETE.
                        loaded.card().dataStatus(), QuoteStatus.CONFIG_INCOMPLETE,
                        "No calculator for pricing model " + s.pricingModel()));
    }

    // ---- response mapping ----

    // /quotes response only: grouped by partner, cheapest marked.
    private static QuoteResponse toResponse(QuoteRequest request, List<QuoteResult> results) {
        // Cheapest priced result overall.
        Optional<QuoteResult.Priced> cheapest = priced(results).min(Comparator.comparing(QuoteResult.Priced::total));

        // Group results by partner id, keeping order.
        Map<Integer, List<QuoteResult>> byPartner = new LinkedHashMap<>();
        // Append each result to its partner's list.
        results.forEach(r -> byPartner.computeIfAbsent(r.partnerId(), k -> new ArrayList<>()).add(r));

        // One PartnerResult per partner:
        List<PartnerResult> partners = byPartner.values().stream().map(list -> {
            // cheapest within the partner,
            Optional<QuoteResult.Priced> partnerCheapest = priced(list).min(Comparator.comparing(QuoteResult.Priced::total));
            // its services as ServiceQuote,
            List<ServiceQuote> quotes = list.stream()
                    // flagging the partner's cheapest.
                    .map(r -> toServiceQuote(r, partnerCheapest.map(p -> p == r).orElse(false)))
                    .toList();
            // Partner id, name, quotes.
            return new PartnerResult(list.get(0).partnerId(), list.get(0).partnerName(), quotes);
        }).toList();

        // SAMPLE if any result is sample,
        DataStatus dataStatus = results.stream().anyMatch(r -> r.dataStatus() == DataStatus.SAMPLE) ? DataStatus.SAMPLE
                // else CONTRACT if any is contract, else null.
                : results.stream().anyMatch(r -> r.dataStatus() == DataStatus.CONTRACT) ? DataStatus.CONTRACT : null;

        // Response: quote id, currency, date, data status,
        return new QuoteResponse(newQuoteId(request), "INR", request.pickupDate(), dataStatus,
                // overall cheapest,
                cheapest.map(p -> new Cheapest(p.partnerId(), p.partnerName(), p.serviceCode(), p.total())).orElse(null),
                // partners.
                partners);
    }

    // One result -> ServiceQuote.
    private static ServiceQuote toServiceQuote(QuoteResult result, boolean cheapestForPartner) {
        // Priced or unavailable?
        return switch (result) {
            // Priced: all price fields,
            case QuoteResult.Priced p -> new ServiceQuote(p.serviceCode(), p.mode(), p.dataStatus(), p.status(), null, null,
                    // zone, weights, slab,
                    p.rateCardVersion(), p.locationKey(), p.volumetricWeightKg(), p.chargeableWeightKg(), p.slab(),
                    // non-zero lines, taxable, GST, total, cheapest flag.
                    visibleLines(p.breakdown()), p.taxableAmount(), p.gst(), p.total(), cheapestForPartner);
            // Unavailable: status, error code, reason;
            case QuoteResult.Unavailable u -> new ServiceQuote(u.serviceCode(), u.mode(), u.dataStatus(), u.status(),
                    // price fields null.
                    u.errorCode(), u.reason(), null, null, null, null, null, null, null, null, null, null);
        };
    }

    /** Hides charges that came to 0 (e.g. not-levied rules stored as 0); BASE is always shown. */
    // Hide zero lines (BASE always shown).
    private static List<ChargeLine> visibleLines(List<ChargeLine> lines) {
        // Filter.
        return lines.stream().filter(l -> l.code().equals(ChargeLine.BASE) || l.amount().signum() != 0).toList();
    }

    // Only priced results.
    private static Stream<QuoteResult.Priced> priced(List<QuoteResult> results) {
        // Filter + cast.
        return results.stream().filter(QuoteResult.Priced.class::isInstance).map(QuoteResult.Priced.class::cast);
    }

    // Quote id:
    private static String newQuoteId(QuoteRequest request) {
        // "Q-" + yyyyMMdd + "-"
        return "Q-" + request.pickupDate().format(QUOTE_DATE) + "-"
                // + first 8 chars of a random UUID, upper case.
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
