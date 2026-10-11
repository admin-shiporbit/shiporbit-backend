package com.shiporbit.backend.rate.core;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Same-city delivery inputs. distanceKm is required only by HYPERLOCAL services
 * (missing -> DISTANCE_REQUIRED for those services, not a validation error for the request).
 * vehicle must match SO_RATE_SLAB.LOCATION_KEY of hyperlocal cards (e.g. BIKE).
 */
public record HyperlocalOptions(
        @Positive @Digits(integer = 4, fraction = 2) BigDecimal distanceKm,
        String vehicle
) {
    public static final HyperlocalOptions NONE = new HyperlocalOptions(null, null);

    public HyperlocalOptions {
        vehicle = (vehicle == null || vehicle.isBlank()) ? null : vehicle.trim().toUpperCase(Locale.ROOT);
    }
}
