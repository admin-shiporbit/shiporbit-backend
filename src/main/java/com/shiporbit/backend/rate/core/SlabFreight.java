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
// Base freight and number of additional steps.
public record SlabFreight(BigDecimal amount, BigDecimal additionalSteps) {

    // Decimal places for EXACT steps.
    private static final int EXACT_STEP_SCALE = 6;

    // Freight from one slab row,
    public static SlabFreight of(BigDecimal firstUnit, BigDecimal firstRate,
                                 BigDecimal addUnit, BigDecimal addRate,
                                 BigDecimal x, Rounding rounding) {
        // x = chargeable kg (or km), rounding = card rounding.
        Objects.requireNonNull(x, "x");
        // x within the first unit, or no additional step defined?
        if (x.compareTo(firstUnit) <= 0 || addUnit.signum() == 0) {
            // -> first rate only.
            return new SlabFreight(money(firstRate), BigDecimal.ZERO);
        }
        // Units above the first unit.
        BigDecimal extra = x.subtract(firstUnit);
        // Steps: EXACT -> fractional,
        BigDecimal steps = rounding == Rounding.EXACT
                // e.g. 2.5 steps,
                ? plain(extra.divide(addUnit, EXACT_STEP_SCALE, RoundingMode.HALF_UP))
                // CEIL_STEP -> rounded up to a whole step.
                : extra.divide(addUnit, 0, RoundingMode.CEILING);
        // Freight = first rate + steps x add rate, 2 dp.
        return new SlabFreight(money(firstRate.add(steps.multiply(addRate))), steps);
    }

    /** Drops trailing zeros without switching to exponent form (600, not 6E+2). */
    // Remove trailing zeros
    private static BigDecimal plain(BigDecimal v) {
        // without exponent form,
        BigDecimal stripped = v.stripTrailingZeros();
        // e.g. 600 not 6E+2.
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    // 2 dp
    private static BigDecimal money(BigDecimal v) {
        // HALF_UP.
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
