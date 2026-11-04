package com.shiporbit.backend.rate.engine;

import com.shiporbit.backend.rate.core.QuoteRequest;
import com.shiporbit.backend.rate.core.QuoteResult;
import com.shiporbit.backend.rate.entity.PricingModel;

import java.util.Set;

/** Prices one service from its rate card. Never throws for business failures: returns Unavailable. */
public interface RateCardCalculator {

    Set<PricingModel> supportedModels();

    QuoteResult quote(QuoteRequest request, RateCardSnapshot card, PartnerLane lane);
}
