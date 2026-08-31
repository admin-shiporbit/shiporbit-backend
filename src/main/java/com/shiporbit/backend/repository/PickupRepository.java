package com.shiporbit.backend.repository;

import com.shiporbit.backend.entity.PickupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface PickupRepository extends JpaRepository<PickupEntity, UUID> {

    List<PickupEntity> findAllByUser_Id(UUID userId);

    Optional<PickupEntity> findByIdAndUser_Id(UUID id, UUID userId);
}
