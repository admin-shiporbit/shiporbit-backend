package com.shiporbit.backend.rate.engine;

import com.shiporbit.backend.rate.core.ChargeableWeight;
import com.shiporbit.backend.rate.core.QuoteRequest;

/** Inputs shared by eligibility rules and the charge engine while pricing one service. */
public record PricingContext(QuoteRequest request, RateCardSnapshot card, PartnerLane lane, ChargeableWeight weight) { }
