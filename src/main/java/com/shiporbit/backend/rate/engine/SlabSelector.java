package com.shiporbit.backend.rate.engine;

import com.shiporbit.backend.rate.entity.PaymentMode;
import com.shiporbit.backend.rate.entity.ShipmentType;
import com.shiporbit.backend.rate.entity.SlabDirection;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Picks the slab row for direction + location + chargeable unit.
 * PAYMENT_MODE / SHIPMENT_TYPE: a row matches if its value is null (any) or equal;
 * among matches the most specific row wins (exact match beats the generic row).
 */
// Static helper.
public final class SlabSelector {

    // No instances.
    private SlabSelector() { }

    // Pick one slab row.
    public static Optional<SlabRow> pick(List<SlabRow> slabs, SlabDirection direction, String locationKey,
                                         BigDecimal x, PaymentMode paymentMode, ShipmentType shipmentType) {
        // From all slabs of the card:
        return slabs.stream()
                // same direction and location key,
                .filter(s -> s.direction() == direction && s.locationKey().equals(locationKey))
                // x within (FROM_UNIT, TO_UNIT],
                .filter(s -> s.covers(x))
                // payment mode null (any) or equal,
                .filter(s -> s.paymentMode() == null || s.paymentMode() == paymentMode)
                // shipment type null (any) or equal,
                .filter(s -> s.shipmentType() == null || s.shipmentType() == shipmentType)
                // most specific row wins (exact payment mode / shipment type beats generic).
                .max(Comparator.comparingInt(SlabRow::specificity));
    }
}
