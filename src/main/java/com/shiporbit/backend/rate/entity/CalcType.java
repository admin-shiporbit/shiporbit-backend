package com.shiporbit.backend.rate.entity;

public enum CalcType {
    FLAT, PERCENT, PER_UNIT,
    /** max(0, FIXED_AMOUNT - base): tops the base (e.g. CHARGE_CODES BASE,WAYBILL) up to a minimum freight. */
    TOP_UP
}
