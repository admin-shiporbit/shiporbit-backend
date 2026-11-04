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
public final class SlabSelector {

    private SlabSelector() { }

    public static Optional<SlabRow> pick(List<SlabRow> slabs, SlabDirection direction, String locationKey,
                                         BigDecimal x, PaymentMode paymentMode, ShipmentType shipmentType) {
        return slabs.stream()
                .filter(s -> s.direction() == direction && s.locationKey().equals(locationKey))
                .filter(s -> s.covers(x))
                .filter(s -> s.paymentMode() == null || s.paymentMode() == paymentMode)
                .filter(s -> s.shipmentType() == null || s.shipmentType() == shipmentType)
                .max(Comparator.comparingInt(SlabRow::specificity));
    }
}
