package com.shiporbit.backend.rate.cache;

import com.shiporbit.backend.entity.DeliveryPartnerEntity;
import com.shiporbit.backend.repository.DeliveryPartnerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cached partner id -> name (SO_DELIVERY_PARTNER_LIST, 14 rows).
 * Its own bean on purpose: when it lived inside RateCardLoader, servicesOn() called it directly,
 * which bypasses Spring's cache proxy (self-invocation), so names were queried twice on a cache miss.
 * Calls from another bean always go through the proxy and hit the cache.
 */
@Service
public class PartnerDirectory {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartnerDirectory.class);

    private final DeliveryPartnerRepository partnerRepo;

    public PartnerDirectory(DeliveryPartnerRepository partnerRepo) {
        this.partnerRepo = partnerRepo;
    }

    @Cacheable(RateCacheConfig.PARTNER_NAMES)
    @Transactional(readOnly = true)
    public Map<Integer, String> names() {
        LOGGER.debug("Rate-card cache MISS: loading partner names from DB");
        return Map.copyOf(partnerRepo.findAll().stream()
                .collect(Collectors.toMap(DeliveryPartnerEntity::getId, DeliveryPartnerEntity::getPartnerName)));
    }
}
