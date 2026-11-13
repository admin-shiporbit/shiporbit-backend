package com.shiporbit.backend.rate.engine;

import com.shiporbit.backend.rate.entity.PricingModel;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Registry: PRICING_MODEL -> calculator, built from every RateCardCalculator bean. No if/else on model. */
// Spring bean.
@Component
// Pricing model -> calculator lookup (used in RateQuoteService.price).
public class CalculatorRegistry {

    // The lookup map.
    private final Map<PricingModel, RateCardCalculator> byModel = new EnumMap<>(PricingModel.class);

    // Spring injects every RateCardCalculator bean (ZoneMapCalculator, HyperlocalCalculator).
    public CalculatorRegistry(List<RateCardCalculator> calculators) {
        // For each calculator:
        for (RateCardCalculator calculator : calculators) {
            // for each model it supports:
            for (PricingModel model : calculator.supportedModels()) {
                // register; remember any previous one.
                RateCardCalculator previous = byModel.put(model, calculator);
                // Two calculators for one model?
                if (previous != null) {
                    // -> fail at startup.
                    throw new IllegalStateException("Two calculators for " + model + ": "
                            + previous.getClass().getSimpleName() + ", " + calculator.getClass().getSimpleName());
                }
            }
        }
    }

    // Calculator for a model.
    public Optional<RateCardCalculator> forModel(PricingModel model) {
        // Empty when none (-> CONFIG_INCOMPLETE).
        return Optional.ofNullable(byModel.get(model));
    }
}
