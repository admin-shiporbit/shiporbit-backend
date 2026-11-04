package com.shiporbit.backend.rate.engine.charge;

import com.shiporbit.backend.rate.engine.ChargeRule;
import com.shiporbit.backend.rate.entity.CalcType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** PER_UNIT: RATE_VALUE x chargeable kg (km for hyperlocal). */
@Component
public class PerUnitChargeHandler implements ChargeHandler {

    @Override
    public CalcType calcType() {
        return CalcType.PER_UNIT;
    }

    @Override
    public BigDecimal amount(ChargeRule rule, BigDecimal baseValue, BigDecimal chargeableUnits) {
        return rule.rateValue().multiply(chargeableUnits);
    }
}
