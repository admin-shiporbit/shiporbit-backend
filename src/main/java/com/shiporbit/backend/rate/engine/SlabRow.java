package com.shiporbit.backend.rate.engine;

import com.shiporbit.backend.rate.entity.PaymentMode;
import com.shiporbit.backend.rate.entity.RateUnit;
import com.shiporbit.backend.rate.entity.ShipmentType;
import com.shiporbit.backend.rate.entity.SlabDirection;

import java.math.BigDecimal;

/** Immutable copy of one SO_RATE_SLAB row. Applies when FROM_UNIT < x <= TO_UNIT (TO_UNIT null = open). */
// One slab: direction, location key, unit (KG / KM),
public record SlabRow(SlabDirection direction, String locationKey, RateUnit unit,
                      // range FROM_UNIT < x <= TO_UNIT,
                      BigDecimal fromUnit, BigDecimal toUnit,
                      // first unit + its rate,
                      BigDecimal firstUnit, BigDecimal firstRate,
                      // additional unit + its rate,
                      BigDecimal addUnit, BigDecimal addRate,
                      // optional payment mode / shipment type filters.
                      PaymentMode paymentMode, ShipmentType shipmentType) {

    // Is x inside this slab?
    public boolean covers(BigDecimal x) {
        // x > FROM_UNIT and (no TO_UNIT or x <= TO_UNIT).
        return x.compareTo(fromUnit) > 0 && (toUnit == null || x.compareTo(toUnit) <= 0);
    }

    /** Number of optional columns this row pins down; the most specific matching row wins. */
    // How specific the row is.
    public int specificity() {
        // +1 per filter set.
        return (paymentMode != null ? 1 : 0) + (shipmentType != null ? 1 : 0);
    }
}
