package com.shiporbit.backend.rate.engine;

import com.shiporbit.backend.rate.entity.PricingModel;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Registry: PRICING_MODEL -> calculator, built from every RateCardCalculator bean. No if/else on model. */
@Component
public class CalculatorRegistry {

    private final Map<PricingModel, RateCardCalculator> byModel = new EnumMap<>(PricingModel.class);

    public CalculatorRegistry(List<RateCardCalculator> calculators) {
        for (RateCardCalculator calculator : calculators) {
            for (PricingModel model : calculator.supportedModels()) {
                RateCardCalculator previous = byModel.put(model, calculator);
                if (previous != null) {
                    throw new IllegalStateException("Two calculators for " + model + ": "
                            + previous.getClass().getSimpleName() + ", " + calculator.getClass().getSimpleName());
                }
            }
        }
    }

    public Optional<RateCardCalculator> forModel(PricingModel model) {
        return Optional.ofNullable(byModel.get(model));
    }
}
