package com.shiporbit.backend.rate.core;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** B2B (part-truck-load) extras. Ignored by non-B2B calculators. */
public record B2bOptions(
        @Min(1) @Max(100) Integer numberOfLRs,
        boolean appointmentDelivery,
        boolean floorDelivery
) {
    public static final B2bOptions DEFAULT = new B2bOptions(1, false, false);

    public B2bOptions {
        numberOfLRs = numberOfLRs == null ? 1 : numberOfLRs;
    }
}
