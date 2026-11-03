package com.shiporbit.backend.rate.engine.charge;

import com.shiporbit.backend.rate.engine.ChargeRule;
import com.shiporbit.backend.rate.entity.CalcType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** FLAT: FIXED_AMOUNT. */
@Component
public class FlatChargeHandler implements ChargeHandler {

    @Override
    public CalcType calcType() {
        return CalcType.FLAT;
    }

    @Override
    public BigDecimal amount(ChargeRule rule, BigDecimal baseValue, BigDecimal chargeableUnits) {
        return rule.fixedAmount();
    }
}
