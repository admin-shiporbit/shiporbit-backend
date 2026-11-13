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
// Spring service bean.
@Service
public class PartnerDirectory {

    // Logger.
    private static final Logger LOGGER = LoggerFactory.getLogger(PartnerDirectory.class);

    // SO_DELIVERY_PARTNER_LIST repository.
    private final DeliveryPartnerRepository partnerRepo;

    // Constructor injection.
    public PartnerDirectory(DeliveryPartnerRepository partnerRepo) {
        // Store.
        this.partnerRepo = partnerRepo;
    }

    // Cached (RateCacheConfig.PARTNER_NAMES): DB hit only on the first call / after eviction.
    @Cacheable(RateCacheConfig.PARTNER_NAMES)
    // Read-only transaction.
    @Transactional(readOnly = true)
    // partnerId -> partner name.
    public Map<Integer, String> names() {
        // Debug log (cache miss only).
        LOGGER.debug("Rate-card cache MISS: loading partner names from DB");
        // All partner rows -> immutable map
        return Map.copyOf(partnerRepo.findAll().stream()
                // id -> PARTNER_NAME (e.g. 14 -> XPRESSBEES).
                .collect(Collectors.toMap(DeliveryPartnerEntity::getId, DeliveryPartnerEntity::getPartnerName)));
    }
}
