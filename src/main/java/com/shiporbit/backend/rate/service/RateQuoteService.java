package com.shiporbit.backend.rate.service;

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
@Service
public class RateQuoteService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateQuoteService.class);
    private static final DateTimeFormatter QUOTE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final RateCardLoader loader;
    private final CalculatorRegistry registry;
    private final LivePartnerQuoteProvider liveProvider;
    private final PartnerAllowlistProperties allowlist;
    private final AtomicBoolean allowlistChecked = new AtomicBoolean();

    public RateQuoteService(RateCardLoader loader, CalculatorRegistry registry, LivePartnerQuoteProvider liveProvider,
                            PartnerAllowlistProperties allowlist) {
        this.loader = loader;
        this.registry = registry;
        this.liveProvider = liveProvider;
        this.allowlist = allowlist;
        if (allowlist.configured()) {
            LOGGER.info("Partners ON for quotes: {}; OFF: {} (shiporbit.partners.enabled / disabled)",
                    allowlist.names(true), allowlist.names(false));
            if (!allowlist.inBoth().isEmpty()) {
                LOGGER.warn("In both shiporbit.partners.enabled and disabled (treated as disabled): {}", allowlist.inBoth());
            }
        } else {
            LOGGER.warn("shiporbit.partners.enabled is not set: every partner not in disabled {} will be quoted",
                    allowlist.names(false));
        }
    }

    public QuoteResponse quote(QuoteRequest request) {
        return toResponse(request, quoteAll(request));
    }

    /**
     * Every partner service's result for the request (priced or not), sorted by partner id then service code.
     * Used by quote() and by RateCompareService (frontend compare format).
     */
    public List<QuoteResult> quoteAll(QuoteRequest request) {
        Map<Integer, String> partnerNames = loader.partnerNames();
        PartnerFilter.requireKnown(request.partners(), partnerNames.values());
        warnUnknownEnabledNamesOnce(partnerNames.values());
        Set<Integer> livePartners = liveProvider.livePartnerIds();

        List<LoadedService> services = loader.servicesOn(request.pickupDate()).stream()
                .filter(s -> !livePartners.contains(s.service().partnerId()))
                .filter(s -> s.service().partnerName() != null && request.includesPartner(s.service().partnerName()))
                .filter(s -> allowlist.allows(s.service().partnerName()))      // shiporbit.partners.enabled
                .toList();
        Map<Integer, PartnerLane> lanes = services.isEmpty() ? Map.of()
                : loader.lanes(request.originPincode(), request.destinationPincode());

        List<QuoteResult> results = new ArrayList<>(liveProvider.quote(request, partnerNames, allowlist::allows));
        services.stream()
                .map(s -> price(s, request, lanes.getOrDefault(s.service().partnerId(), PartnerLane.EMPTY)))
                .forEach(results::add);
        results.sort(Comparator.comparing(QuoteResult::partnerId).thenComparing(QuoteResult::serviceCode));
        return results;
    }

    /** A typo in shiporbit.partners.enabled silently disables a partner, so say so once (names come from the DB). */
    private void warnUnknownEnabledNamesOnce(java.util.Collection<String> knownNames) {
        if (!allowlist.configured() || !allowlistChecked.compareAndSet(false, true)) {
            return;
        }
        List<String> unknown = allowlist.allNames().stream()
                .filter(name -> knownNames.stream().noneMatch(known -> PartnerAllowlistProperties.sameName(name, known)))
                .toList();
        if (!unknown.isEmpty()) {
            LOGGER.warn("shiporbit.partners.enabled/disabled have names not in SO_DELIVERY_PARTNER_LIST (ignored): {}", unknown);
        }
        List<String> unlisted = knownNames.stream()
                .filter(known -> allowlist.allNames().stream().noneMatch(name -> PartnerAllowlistProperties.sameName(name, known)))
                .sorted()
                .toList();
        if (!unlisted.isEmpty()) {
            LOGGER.warn("Partners in neither shiporbit.partners.enabled nor disabled (not quoted): {}", unlisted);
        }
    }

    private QuoteResult price(LoadedService loaded, QuoteRequest request, PartnerLane lane) {
        ServiceInfo s = loaded.service();
        if (loaded.card() == null) {
            return new QuoteResult.Unavailable(s.partnerId(), s.partnerName(), s.serviceCode(), s.mode(), null,
                    QuoteStatus.RATE_UNAVAILABLE, "No ACTIVE rate card on " + request.pickupDate());
        }
        return registry.forModel(s.pricingModel())
                .map(calculator -> calculator.quote(request, loaded.card(), lane))
                .orElseGet(() -> new QuoteResult.Unavailable(s.partnerId(), s.partnerName(), s.serviceCode(), s.mode(),
                        loaded.card().dataStatus(), QuoteStatus.CONFIG_INCOMPLETE,
                        "No calculator for pricing model " + s.pricingModel()));
    }

    // ---- response mapping ----

    private static QuoteResponse toResponse(QuoteRequest request, List<QuoteResult> results) {
        Optional<QuoteResult.Priced> cheapest = priced(results).min(Comparator.comparing(QuoteResult.Priced::total));

        Map<Integer, List<QuoteResult>> byPartner = new LinkedHashMap<>();
        results.forEach(r -> byPartner.computeIfAbsent(r.partnerId(), k -> new ArrayList<>()).add(r));

        List<PartnerResult> partners = byPartner.values().stream().map(list -> {
            Optional<QuoteResult.Priced> partnerCheapest = priced(list).min(Comparator.comparing(QuoteResult.Priced::total));
            List<ServiceQuote> quotes = list.stream()
                    .map(r -> toServiceQuote(r, partnerCheapest.map(p -> p == r).orElse(false)))
                    .toList();
            return new PartnerResult(list.get(0).partnerId(), list.get(0).partnerName(), quotes);
        }).toList();

        DataStatus dataStatus = results.stream().anyMatch(r -> r.dataStatus() == DataStatus.SAMPLE) ? DataStatus.SAMPLE
                : results.stream().anyMatch(r -> r.dataStatus() == DataStatus.CONTRACT) ? DataStatus.CONTRACT : null;

        return new QuoteResponse(newQuoteId(request), "INR", request.pickupDate(), dataStatus,
                cheapest.map(p -> new Cheapest(p.partnerId(), p.partnerName(), p.serviceCode(), p.total())).orElse(null),
                partners);
    }

    private static ServiceQuote toServiceQuote(QuoteResult result, boolean cheapestForPartner) {
        return switch (result) {
            case QuoteResult.Priced p -> new ServiceQuote(p.serviceCode(), p.mode(), p.dataStatus(), p.status(), null,
                    p.rateCardVersion(), p.locationKey(), p.volumetricWeightKg(), p.chargeableWeightKg(), p.slab(),
                    visibleLines(p.breakdown()), p.taxableAmount(), p.gst(), p.total(), cheapestForPartner);
            case QuoteResult.Unavailable u -> new ServiceQuote(u.serviceCode(), u.mode(), u.dataStatus(), u.status(),
                    u.reason(), null, null, null, null, null, null, null, null, null, null);
        };
    }

    /** Hides charges that came to 0 (e.g. not-levied rules stored as 0); BASE is always shown. */
    private static List<ChargeLine> visibleLines(List<ChargeLine> lines) {
        return lines.stream().filter(l -> l.code().equals(ChargeLine.BASE) || l.amount().signum() != 0).toList();
    }

    private static Stream<QuoteResult.Priced> priced(List<QuoteResult> results) {
        return results.stream().filter(QuoteResult.Priced.class::isInstance).map(QuoteResult.Priced.class::cast);
    }

    private static String newQuoteId(QuoteRequest request) {
        return "Q-" + request.pickupDate().format(QUOTE_DATE) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
