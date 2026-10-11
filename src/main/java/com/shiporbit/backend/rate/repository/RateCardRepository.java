package com.shiporbit.backend.rate.repository;

import com.shiporbit.backend.rate.entity.CardStatus;
import com.shiporbit.backend.rate.entity.RateCardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RateCardRepository extends JpaRepository<RateCardEntity, Long> {

    @Query("""
           select c from RateCardEntity c
           where c.serviceId = :serviceId
             and c.status = :status
             and c.effectiveFrom <= :date
             and (c.effectiveTo is null or c.effectiveTo >= :date)
           """)
    Optional<RateCardEntity> findByServiceAndStatusOn(@Param("serviceId") Long serviceId,
                                                     @Param("status") CardStatus status,
                                                     @Param("date") LocalDate date);

    default Optional<RateCardEntity> findActiveFor(Long serviceId, LocalDate pickupDate) {
        return findByServiceAndStatusOn(serviceId, CardStatus.ACTIVE, pickupDate);
    }

    @Query("""
           select c from RateCardEntity c
           where c.status = :status
             and c.effectiveFrom <= :date
             and (c.effectiveTo is null or c.effectiveTo >= :date)
           """)
    List<RateCardEntity> findAllByStatusOn(@Param("status") CardStatus status, @Param("date") LocalDate date);

    /** Every ACTIVE card valid on the date, all services in one query. */
    default List<RateCardEntity> findAllActiveOn(LocalDate date) {
        return findAllByStatusOn(CardStatus.ACTIVE, date);
    }
}
