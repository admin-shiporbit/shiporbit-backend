package com.shiporbit.backend.rate.core;

import com.shiporbit.backend.rate.entity.RateUnit;

import java.math.BigDecimal;

/** How base freight was derived from the matched slab; returned for transparency. */
public record SlabInfo(
        RateUnit unit,
        BigDecimal firstUnit,
        BigDecimal firstRate,
        BigDecimal additionalUnit,
        BigDecimal additionalSteps,     // whole steps (CEIL_STEP) or exact fraction (EXACT)
        BigDecimal additionalRate
) { }
