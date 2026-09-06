package com.shiporbit.backend.rate;

import com.shiporbit.backend.exception.PartnerNotServiceableException;
import com.shiporbit.backend.rate.dto.RateBreakdown;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Strategy context. Spring injects every {@link DeliveryRateStrategy} bean here
 * (one per delivery partner); callers pick a partner by its code without knowing
 * which concrete strategy class handles it.
 */
@Component
public class RateCalculator {

    /** Lightweight, framework-agnostic summary of a registered partner. */
    public record PartnerInfo(String code, String name) {
    }

    private final Map<String, DeliveryRateStrategy> strategiesByPartnerCode;

    public RateCalculator(List<DeliveryRateStrategy> strategies) {
        this.strategiesByPartnerCode = strategies.stream()
                .collect(Collectors.toMap(
                        DeliveryRateStrategy::getPartnerCode,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate delivery partner code registered: " + a.getPartnerCode());
                        },
                        LinkedHashMap::new));
    }

    public RateBreakdown calculate(String partnerCode, ShipmentRequest request) {
        DeliveryRateStrategy strategy = requirePartner(partnerCode);
        if (!strategy.isServiceable(request)) {
            throw new PartnerNotServiceableException(
                    strategy.getPartnerName() + " does not service this shipment");
        }
        RateBreakdown breakdown = strategy.calculateRate(request);
        GstCalculator.apply(breakdown, request);
        return breakdown;
    }

    /** Rates from every partner that can service this shipment - handy for a "compare rates" view. */
    public Map<String, RateBreakdown> calculateForAllServiceablePartners(ShipmentRequest request) {
        Map<String, RateBreakdown> results = new LinkedHashMap<>();
        for (DeliveryRateStrategy strategy : strategiesByPartnerCode.values()) {
            if (strategy.isServiceable(request)) {
                RateBreakdown breakdown = strategy.calculateRate(request);
                GstCalculator.apply(breakdown, request);
                results.put(strategy.getPartnerCode(), breakdown);
            }
        }
        return results;
    }

    public List<String> getAvailablePartnerCodes() {
        return List.copyOf(strategiesByPartnerCode.keySet());
    }

    public List<PartnerInfo> getAvailablePartners() {
        return strategiesByPartnerCode.values().stream()
                .map(s -> new PartnerInfo(s.getPartnerCode(), s.getPartnerName()))
                .toList();
    }

    private DeliveryRateStrategy requirePartner(String partnerCode) {
        DeliveryRateStrategy strategy = strategiesByPartnerCode.get(partnerCode);
        if (strategy == null) {
            throw new IllegalArgumentException("No delivery partner registered for code: " + partnerCode);
        }
        return strategy;
    }
}
