package com.shiporbit.backend.rate.engine.charge;

import com.shiporbit.backend.rate.engine.ChargeRule;
import com.shiporbit.backend.rate.entity.CalcType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * TOP_UP: max(0, FIXED_AMOUNT - base). A minimum that applies to some lines only, e.g. XpressBees
 * "Min Freight (Waybill + Basic Rates Only) Rs 350": BASE = CHARGE_CODES "BASE,WAYBILL", FIXED_AMOUNT = 350,
 * SEQ after WAYBILL. Unlike the card MIN_BILL it ignores fuel, FOV and other lines.
 */
// Spring bean.
@Component
// TOP_UP: whatever is missing to reach a minimum.
public class TopUpChargeHandler implements ChargeHandler {

    @Override
    // Handles
    public CalcType calcType() {
        // CalcType.TOP_UP.
        return CalcType.TOP_UP;
    }

    @Override
    // Amount:
    public BigDecimal amount(ChargeRule rule, BigDecimal baseValue, BigDecimal chargeableUnits) {
        // gap = FIXED_AMOUNT - base value (e.g. 350 - (BASE + WAYBILL)),
        BigDecimal gap = rule.fixedAmount().subtract(baseValue);
        // positive gap, else 0.
        return gap.signum() > 0 ? gap : BigDecimal.ZERO;
    }
}
