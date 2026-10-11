package com.shiporbit.backend.rate.engine.charge;

import com.shiporbit.backend.rate.engine.ChargeRule;
import com.shiporbit.backend.rate.entity.CalcType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** PERCENT: FIXED_AMOUNT + RATE_VALUE% x base. */
@Component
public class PercentChargeHandler implements ChargeHandler {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Override
    public CalcType calcType() {
        return CalcType.PERCENT;
    }

    @Override
    public BigDecimal amount(ChargeRule rule, BigDecimal baseValue, BigDecimal chargeableUnits) {
        return rule.fixedAmount().add(rule.rateValue().multiply(baseValue).divide(HUNDRED));
    }
}
