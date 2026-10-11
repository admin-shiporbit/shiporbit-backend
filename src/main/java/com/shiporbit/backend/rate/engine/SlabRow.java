package com.shiporbit.backend.rate.engine;

import com.shiporbit.backend.rate.entity.PaymentMode;
import com.shiporbit.backend.rate.entity.RateUnit;
import com.shiporbit.backend.rate.entity.ShipmentType;
import com.shiporbit.backend.rate.entity.SlabDirection;

import java.math.BigDecimal;

/** Immutable copy of one SO_RATE_SLAB row. Applies when FROM_UNIT < x <= TO_UNIT (TO_UNIT null = open). */
public record SlabRow(SlabDirection direction, String locationKey, RateUnit unit,
                      BigDecimal fromUnit, BigDecimal toUnit,
                      BigDecimal firstUnit, BigDecimal firstRate,
                      BigDecimal addUnit, BigDecimal addRate,
                      PaymentMode paymentMode, ShipmentType shipmentType) {

    public boolean covers(BigDecimal x) {
        return x.compareTo(fromUnit) > 0 && (toUnit == null || x.compareTo(toUnit) <= 0);
    }

    /** Number of optional columns this row pins down; the most specific matching row wins. */
    public int specificity() {
        return (paymentMode != null ? 1 : 0) + (shipmentType != null ? 1 : 0);
    }
}
