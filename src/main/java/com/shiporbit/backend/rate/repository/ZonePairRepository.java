package com.shiporbit.backend.rate.repository;

import com.shiporbit.backend.rate.entity.ZonePairEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read in full by ZoneConfigLoader (small table, cached). */
public interface ZonePairRepository extends JpaRepository<ZonePairEntity, Long> {
}
