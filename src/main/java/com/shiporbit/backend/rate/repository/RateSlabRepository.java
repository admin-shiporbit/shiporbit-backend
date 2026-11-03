package com.shiporbit.backend.rate.repository;

import com.shiporbit.backend.rate.entity.RateSlabEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RateSlabRepository extends JpaRepository<RateSlabEntity, Long> {

    List<RateSlabEntity> findByRateCardIdOrderByDirectionAscLocationKeyAscFromUnitAsc(Long rateCardId);



    /** All rows of several cards in one query (rate-card loader). */
    List<RateSlabEntity> findByRateCardIdIn(Collection<Long> rateCardIds);
}
