package com.shiporbit.backend.rate.engine.charge;

import com.shiporbit.backend.rate.engine.ChargeRule;
import com.shiporbit.backend.rate.entity.CalcType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** PER_UNIT: RATE_VALUE x chargeable kg (km for hyperlocal). */
// Spring bean.
@Component
// PER_UNIT: rate x chargeable units.
public class PerUnitChargeHandler implements ChargeHandler {

    @Override
    // Handles
    public CalcType calcType() {
        // CalcType.PER_UNIT.
        return CalcType.PER_UNIT;
    }

    @Override
    // Amount =
    public BigDecimal amount(ChargeRule rule, BigDecimal baseValue, BigDecimal chargeableUnits) {
        // RATE_VALUE x chargeable kg (or km) (e.g. ODA Rs 3/kg, state surcharge Rs 2/kg).
        return rule.rateValue().multiply(chargeableUnits);
    }
}
