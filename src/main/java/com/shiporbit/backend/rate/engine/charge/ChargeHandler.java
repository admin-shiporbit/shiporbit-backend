package com.shiporbit.backend.rate.engine.charge;

import com.shiporbit.backend.rate.engine.ChargeRule;
import com.shiporbit.backend.rate.entity.CalcType;

import java.math.BigDecimal;

/** Strategy for one CALC_TYPE. Returns the raw amount; ChargeEngine applies MIN/MAX and rounding. */
public interface ChargeHandler {

    CalcType calcType();

    /**
     * @param baseValue       value of the rule's BASE (freight, COD amount, declared value, ...)
     * @param chargeableUnits chargeable kg (km for hyperlocal), used by PER_UNIT
     */
    BigDecimal amount(ChargeRule rule, BigDecimal baseValue, BigDecimal chargeableUnits);
}
