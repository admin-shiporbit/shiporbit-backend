package com.shiporbit.backend.rate.repository;

import com.shiporbit.backend.rate.entity.RateServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RateServiceRepository extends JpaRepository<RateServiceEntity, Long> {

    List<RateServiceEntity> findByEnabledTrue();

    Optional<RateServiceEntity> findByPartnerIdAndServiceCode(Integer partnerId, String serviceCode);
}
