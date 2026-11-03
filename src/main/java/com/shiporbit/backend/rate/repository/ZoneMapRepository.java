package com.shiporbit.backend.rate.repository;

import com.shiporbit.backend.rate.entity.ZoneMapEntity;
import com.shiporbit.backend.rate.entity.ZoneMapId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZoneMapRepository extends JpaRepository<ZoneMapEntity, ZoneMapId> {

    // Zone for this lane, for ALL partners and pricing models, in one query
    List<ZoneMapEntity> findByIdOriginPincodeAndIdDestinationPincode(String origin, String destination);
}