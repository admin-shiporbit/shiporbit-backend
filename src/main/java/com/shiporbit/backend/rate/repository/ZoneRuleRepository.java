package com.shiporbit.backend.rate.repository;

import com.shiporbit.backend.rate.entity.ZoneRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read in full by ZoneConfigLoader (small table, cached). */
public interface ZoneRuleRepository extends JpaRepository<ZoneRuleEntity, Long> {
}
