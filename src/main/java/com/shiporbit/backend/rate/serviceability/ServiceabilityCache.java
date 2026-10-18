package com.shiporbit.backend.rate.serviceability;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Caches definite partner serviceability answers (SERVICEABLE / NOT_SERVICEABLE) so each pincode or lane
 * is asked once per TTL, not on every quote. UNKNOWN results are never cached.
 * Separate from RateCacheConfig on purpose: serviceability changes rarely (hours), rate-card caches use
 * shiporbit.rates.cache-ttl-minutes. TTL: shiporbit.rates.serviceability-ttl-minutes (default 720 = 12 h, 0 = off).
 */
@Component
public class ServiceabilityCache {

    private final Cache<String, Serviceability> cache;

    public ServiceabilityCache(@Value("${shiporbit.rates.serviceability-ttl-minutes:720}") long ttlMinutes,
                               @Value("${shiporbit.rates.serviceability-cache-size:50000}") long maxSize) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                .maximumSize(ttlMinutes <= 0 ? 0 : maxSize)
                .recordStats()
                .build();
    }

    /** key should be "<partner>:<api>:<params>", e.g. "delhivery:b2c:pin:560001". */
    public Serviceability get(String key, Supplier<Serviceability> loader) {
        Serviceability cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        Serviceability fresh = loader.get();
        if (fresh.cacheable()) {
            cache.put(key, fresh);
        }
        return fresh;
    }

    public void evictAll() {
        cache.invalidateAll();
    }
}
