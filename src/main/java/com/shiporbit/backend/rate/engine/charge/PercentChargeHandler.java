package com.shiporbit.backend.rate.engine.charge;

import com.shiporbit.backend.rate.engine.ChargeRule;
import com.shiporbit.backend.rate.entity.CalcType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** PERCENT: FIXED_AMOUNT + RATE_VALUE% x base. */
// Spring bean.
@Component
// PERCENT: fixed + % of base value.
public class PercentChargeHandler implements ChargeHandler {

    // 100.
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Override
    // Handles
    public CalcType calcType() {
        // CalcType.PERCENT.
        return CalcType.PERCENT;
    }

    @Override
    // Amount =
    public BigDecimal amount(ChargeRule rule, BigDecimal baseValue, BigDecimal chargeableUnits) {
        // FIXED_AMOUNT + RATE_VALUE x base / 100 (e.g. fuel 25% of BASE; heavy handling 100% of actual kg = Rs 1/kg).
        return rule.fixedAmount().add(rule.rateValue().multiply(baseValue).divide(HUNDRED));
    }
}
