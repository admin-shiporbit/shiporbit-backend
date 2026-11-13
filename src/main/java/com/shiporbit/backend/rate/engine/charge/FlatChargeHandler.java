package com.shiporbit.backend.rate.engine.charge;

import com.shiporbit.backend.rate.engine.ChargeRule;
import com.shiporbit.backend.rate.entity.CalcType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** FLAT: FIXED_AMOUNT. */
// Spring bean.
@Component
// FLAT: fixed amount.
public class FlatChargeHandler implements ChargeHandler {

    @Override
    // Handles
    public CalcType calcType() {
        // CalcType.FLAT.
        return CalcType.FLAT;
    }

    @Override
    // Amount =
    public BigDecimal amount(ChargeRule rule, BigDecimal baseValue, BigDecimal chargeableUnits) {
        // FIXED_AMOUNT (e.g. waybill Rs 100).
        return rule.fixedAmount();
    }
}
