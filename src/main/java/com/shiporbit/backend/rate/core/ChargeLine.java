package com.shiporbit.backend.rate.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** One line of a price breakdown. Freight is code BASE; amounts are rounded to 2 dp, HALF_UP. */
public record ChargeLine(String code, BigDecimal amount) {

    public static final String BASE = "BASE";

    public ChargeLine {
        Objects.requireNonNull(code, "code");
        amount = Objects.requireNonNull(amount, "amount").setScale(2, RoundingMode.HALF_UP);
    }
}
