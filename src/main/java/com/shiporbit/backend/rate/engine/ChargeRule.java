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
// One charge rule: code, SEQ, FLAT / PERCENT / PER_UNIT / TOP_UP,
public record ChargeRule(String chargeCode, int seq, CalcType calcType,
                         // rate (% or per unit), fixed amount,
                         BigDecimal rateValue, BigDecimal fixedAmount,
                         // min / max clamp,
                         BigDecimal minAmount, BigDecimal maxAmount,
                         // base kind, base codes (for CHARGE_CODES),
                         ChargeBase base, List<String> baseCodes,
                         // tier range,
                         BigDecimal tierFrom, BigDecimal tierTo,
                         // condition + its value,
                         AppliesWhen appliesWhen, String appliesValue,
                         // directions it applies to,
                         Set<ShipmentDirection> directions,
                         // validity dates.
                         LocalDate validFrom, LocalDate validTo) {

    // Compact constructor:
    public ChargeRule {
        // base codes null -> empty, immutable,
        baseCodes = baseCodes == null ? List.of() : List.copyOf(baseCodes);
        // directions immutable.
        directions = Set.copyOf(directions);
    }

    // Valid on this date?
    public boolean validOn(LocalDate date) {
        // Not before VALID_FROM
        return (validFrom == null || !date.isBefore(validFrom))
                // and not after VALID_TO (null = open).
                && (validTo == null || !date.isAfter(validTo));
    }

    // For this direction?
    public boolean appliesTo(ShipmentDirection direction) {
        // In DIRECTIONS.
        return directions.contains(direction);
    }

    /** TIER_FROM <= value < TIER_TO; no tier = always in. */
    // Base value inside the tier?
    public boolean inTier(BigDecimal value) {
        // >= TIER_FROM (if set)
        return (tierFrom == null || value.compareTo(tierFrom) >= 0)
                // and < TIER_TO (if set).
                && (tierTo == null || value.compareTo(tierTo) < 0);
    }
}
