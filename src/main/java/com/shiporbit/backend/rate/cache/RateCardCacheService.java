package com.shiporbit.backend.rate.cache;

import com.shiporbit.backend.rate.serviceability.ServiceabilityCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

/**
 * Clears every rate-card cache. Call after anything changes rate data:
 * card import / activation / retirement (step 6), pincode or zone uploads, zone rule changes,
 * or a manual DB edit (POST /api/v1/rate-cards/cache/evict, ADMIN only).
 */
@Service
public class RateCardCacheService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateCardCacheService.class);

    private final ServiceabilityCache serviceabilityCache;

    public RateCardCacheService(ServiceabilityCache serviceabilityCache) {
        this.serviceabilityCache = serviceabilityCache;
    }

    // LANES holds zone keys worked out from ZONE_CONFIG, so the two are always cleared together.
    @CacheEvict(cacheNames = {RateCacheConfig.SERVICES, RateCacheConfig.PARTNER_NAMES, RateCacheConfig.LANES,
            RateCacheConfig.ZONE_CONFIG},
            allEntries = true)
    public void evictAll() {
        serviceabilityCache.evictAll();
        LOGGER.info("Rate-card and partner serviceability caches cleared");
    }
}
