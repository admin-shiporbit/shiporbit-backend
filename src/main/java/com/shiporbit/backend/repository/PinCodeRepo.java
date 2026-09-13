package com.shiporbit.backend.repository;

import com.shiporbit.backend.entity.PincodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PinCodeRepo extends JpaRepository<PincodeEntity, Long> {
}
