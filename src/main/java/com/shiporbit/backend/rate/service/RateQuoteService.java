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
import com.shiporbit.backend.rate.service.RateCardLoader.LoadedService;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Prices one request against every enabled rate-card service (optionally filtered by partner name).
 * Load (fixed number of queries) -> calculator per pricing model -> group by partner, mark cheapest.
 * Live partner APIs are not included yet (step 5 adapters).
 */
@Service
public class RateQuoteService {

    private static final DateTimeFormatter QUOTE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final RateCardLoader loader;
    private final CalculatorRegistry registry;

    public RateQuoteService(RateCardLoader loader, CalculatorRegistry registry) {
        this.loader = loader;
        this.registry = registry;
    }

    public QuoteResponse quote(QuoteRequest request) {
        List<LoadedService> services = loader.servicesOn(request.pickupDate()).stream()
                .filter(s -> s.service().partnerName() != null && request.includesPartner(s.service().partnerName()))
                .toList();
        Map<Integer, PartnerLane> lanes = services.isEmpty() ? Map.of()
                : loader.lanes(request.originPincode(), request.destinationPincode());

        List<QuoteResult> results = services.stream()
                .map(s -> price(s, request, lanes.getOrDefault(s.service().partnerId(), PartnerLane.EMPTY)))
                .toList();
        return toResponse(request, results);
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
