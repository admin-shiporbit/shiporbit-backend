package com.shiporbit.backend.rate.service;

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
@Service
public class RateCompareService {

    private final RateQuoteService quoteService;
    private final RateCardLoader loader;
    private final PartnerProfileProperties profiles;

    public RateCompareService(RateQuoteService quoteService, RateCardLoader loader, PartnerProfileProperties profiles) {
        this.quoteService = quoteService;
        this.loader = loader;
        this.profiles = profiles;
    }

    public CompareRatesResponse compare(CompareRatesRequest request) {
        Set<ServiceMode> modes = CompareRatesMapper.modesFor(request.deliveryMode());
        QuoteRequest quote = CompareRatesMapper.toQuoteRequest(request);
        ChargeableWeight weights = CompareRatesMapper.displayWeights(quote);

        Map<Integer, String> partnerNames = loader.partnerNames();
        Map<String, RateCardSnapshot> cards = new HashMap<>();          // cached; for COD rule + max weight
        for (LoadedService s : loader.servicesOn(quote.pickupDate())) {
            if (s.card() != null) {
                cards.put(key(s.service().partnerId(), s.service().serviceCode()), s.card());
            }
        }

        List<CarrierResult> results = new ArrayList<>();
        List<Unavailable> unavailable = new ArrayList<>();
        boolean sample = false;
        for (QuoteResult r : quoteService.quoteAll(quote)) {
            if (!modes.contains(r.mode())) {
                continue;                                   // not the delivery mode asked for
            }
            Profile profile = profiles.forPartner(r.partnerId(), partnerNames.get(r.partnerId()));
            switch (r) {
                case QuoteResult.Priced p -> {
                    results.add(new CarrierResult(
                            CompareRatesMapper.carrier(p, profile, cards.get(key(p.partnerId(), p.serviceCode()))),
                            CompareRatesMapper.fare(p, weights.chargeableWeightKg())));
                    sample |= p.dataStatus() == DataStatus.SAMPLE;
                }
                case QuoteResult.Unavailable u ->
                        unavailable.add(new Unavailable(profile.displayName(), u.serviceCode(), u.status(), u.errorCode(), u.reason()));
            }
        }
        results.sort(Comparator.comparing((CarrierResult c) -> c.result().total()).thenComparing(c -> c.carrier().name()));

        int partnerCount = (int) results.stream().map(c -> c.carrier().name()).distinct().count();
        boolean etdForAll = !results.isEmpty() && results.stream().allMatch(c -> Objects.nonNull(c.carrier().etd()));

        return new CompareRatesResponse(true, "INR", sample, request.deliveryMode(), request.fromPin(), request.toPin(),
                request.weight(), request.weightUnit(), request.length(), request.width(), request.height(),
                request.dimensionUnit(), request.cod(), request.orderValue(),
                CompareRatesMapper.plain(weights.deadWeightKg()), CompareRatesMapper.plain(weights.volumetricWeightKg()),
                CompareRatesMapper.plain(weights.chargeableWeightKg()), partnerCount, "fare",
                CompareRatesMapper.sortOptions(etdForAll), results, unavailable);
    }

    private static String key(Integer partnerId, String serviceCode) {
        return partnerId + "/" + serviceCode;
    }
}
