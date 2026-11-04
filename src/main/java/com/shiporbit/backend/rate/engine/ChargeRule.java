package com.shiporbit.backend.rate.engine;

import com.shiporbit.backend.rate.entity.AppliesWhen;
import com.shiporbit.backend.rate.entity.CalcType;
import com.shiporbit.backend.rate.entity.ChargeBase;
import com.shiporbit.backend.rate.entity.ShipmentDirection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Immutable copy of one SO_RATE_CHARGE_RULE row. */
public record ChargeRule(String chargeCode, int seq, CalcType calcType,
                         BigDecimal rateValue, BigDecimal fixedAmount,
                         BigDecimal minAmount, BigDecimal maxAmount,
                         ChargeBase base, List<String> baseCodes,
                         BigDecimal tierFrom, BigDecimal tierTo,
                         AppliesWhen appliesWhen, String appliesValue,
                         Set<ShipmentDirection> directions,
                         LocalDate validFrom, LocalDate validTo) {

    public ChargeRule {
        baseCodes = baseCodes == null ? List.of() : List.copyOf(baseCodes);
        directions = Set.copyOf(directions);
    }

    public boolean validOn(LocalDate date) {
        return (validFrom == null || !date.isBefore(validFrom))
                && (validTo == null || !date.isAfter(validTo));
    }

    public boolean appliesTo(ShipmentDirection direction) {
        return directions.contains(direction);
    }

    /** TIER_FROM <= value < TIER_TO; no tier = always in. */
    public boolean inTier(BigDecimal value) {
        return (tierFrom == null || value.compareTo(tierFrom) >= 0)
                && (tierTo == null || value.compareTo(tierTo) < 0);
    }
}
