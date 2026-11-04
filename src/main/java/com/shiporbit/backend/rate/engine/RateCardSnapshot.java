package com.shiporbit.backend.rate.engine;

import com.shiporbit.backend.rate.core.WeightRules;
import com.shiporbit.backend.rate.entity.DataStatus;
import com.shiporbit.backend.rate.entity.Rounding;
import com.shiporbit.backend.rate.entity.RtoRule;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Everything needed to price one service: the ACTIVE card with its slabs and rules,
 * copied out of JPA into immutable records so it can be cached and used without a DB session.
 */
public record RateCardSnapshot(
        ServiceInfo service,
        Long cardId,
        String versionLabel,
        DataStatus dataStatus,
        WeightRules weightRules,
        BigDecimal maxWeightKg,
        BigDecimal maxDistanceKm,
        RtoRule rtoRule,
        BigDecimal rtoPercent,
        BigDecimal minBill,
        boolean ratesIncludeGst,
        Rounding rounding,
        List<SlabRow> slabs,
        List<ChargeRule> rules        // sorted by SEQ
) {
    public RateCardSnapshot {
        slabs = List.copyOf(slabs);
        rules = rules.stream().sorted(Comparator.comparingInt(ChargeRule::seq)).toList();
    }
}
