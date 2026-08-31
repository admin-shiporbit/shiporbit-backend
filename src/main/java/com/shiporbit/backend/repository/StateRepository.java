package com.shiporbit.backend.repository;

import com.shiporbit.backend.entity.StateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StateRepository extends JpaRepository<StateEntity, Integer> {

    Optional<StateEntity> findByCodeIgnoreCase(String code);
}
