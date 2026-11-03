package com.shiporbit.backend.rate.engine;

import com.shiporbit.backend.rate.core.QuoteStatus;

/** Why a service cannot be priced. */
public record Rejection(QuoteStatus status, String reason) { }
