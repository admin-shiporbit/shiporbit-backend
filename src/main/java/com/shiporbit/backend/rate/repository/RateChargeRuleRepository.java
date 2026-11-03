package com.shiporbit.backend.rate.repository;

import com.shiporbit.backend.rate.entity.RateChargeRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RateChargeRuleRepository extends JpaRepository<RateChargeRuleEntity, Long> {

    List<RateChargeRuleEntity> findByRateCardIdOrderBySeqAsc(Long rateCardId);

    /** All rows of several cards in one query (rate-card loader). */
    List<RateChargeRuleEntity> findByRateCardIdIn(Collection<Long> rateCardIds);
}
