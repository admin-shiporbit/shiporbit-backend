package com.shiporbit.backend.rate.core;

import com.shiporbit.backend.rate.entity.Rounding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Base freight from one slab row:
 * <pre>
 *   x <= FIRST_UNIT or ADD_UNIT = 0 :  FIRST_RATE
 *   CEIL_STEP : FIRST_RATE + CEIL((x - FIRST_UNIT) / ADD_UNIT) * ADD_RATE
 *   EXACT     : FIRST_RATE +      (x - FIRST_UNIT) / ADD_UNIT  * ADD_RATE
 * </pre>
 * Amount rounded to 2 dp, HALF_UP. x is chargeable kg (or km for hyperlocal).
 */
public record SlabFreight(BigDecimal amount, BigDecimal additionalSteps) {

    private static final int EXACT_STEP_SCALE = 6;

    public static SlabFreight of(BigDecimal firstUnit, BigDecimal firstRate,
                                 BigDecimal addUnit, BigDecimal addRate,
                                 BigDecimal x, Rounding rounding) {
        Objects.requireNonNull(x, "x");
        if (x.compareTo(firstUnit) <= 0 || addUnit.signum() == 0) {
            return new SlabFreight(money(firstRate), BigDecimal.ZERO);
        }
        BigDecimal extra = x.subtract(firstUnit);
        BigDecimal steps = rounding == Rounding.EXACT
                ? plain(extra.divide(addUnit, EXACT_STEP_SCALE, RoundingMode.HALF_UP))
                : extra.divide(addUnit, 0, RoundingMode.CEILING);
        return new SlabFreight(money(firstRate.add(steps.multiply(addRate))), steps);
    }

    /** Drops trailing zeros without switching to exponent form (600, not 6E+2). */
    private static BigDecimal plain(BigDecimal v) {
        BigDecimal stripped = v.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
