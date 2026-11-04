package com.shiporbit.backend.rate.repository;

import com.shiporbit.backend.rate.entity.PartnerPincodeEntity;
import com.shiporbit.backend.rate.entity.PartnerPincodeId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PartnerPincodeRepository extends JpaRepository<PartnerPincodeEntity, PartnerPincodeId> {

    // Origin + destination for ALL partners in one query
    List<PartnerPincodeEntity> findByIdPincodeIn(Collection<String> pincodes);
}