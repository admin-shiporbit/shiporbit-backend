package com.shiporbit.backend.rate.serviceability;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.shiporbit.backend.logging.Flow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
// Spring bean shared by all live clients.
@Component
public class ServiceabilityCache {

    // Logger.
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceabilityCache.class);

    // Caffeine cache: key -> answer.
    private final Cache<String, Serviceability> cache;

    // TTL minutes (default 720 = 12 h)
    public ServiceabilityCache(@Value("${shiporbit.rates.serviceability-ttl-minutes:720}") long ttlMinutes,
                               // and max entries (default 50,000) from application.yaml.
                               @Value("${shiporbit.rates.serviceability-cache-size:50000}") long maxSize) {
        // Build the cache:
        this.cache = Caffeine.newBuilder()
                // entry expires TTL after it was written,
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                // size 0 when TTL <= 0 (cache off),
                .maximumSize(ttlMinutes <= 0 ? 0 : maxSize)
                // keep hit / miss stats,
                .recordStats()
                // create.
                .build();
    }

    /** key should be "<partner>:<api>:<params>", e.g. "delhivery:b2c:pin:560001". */
    // Get from cache or call the partner.
    public Serviceability get(String key, Supplier<Serviceability> loader) {
        // Cached answer?
        Serviceability cached = cache.getIfPresent(key);
        // Hit?
        if (cached != null) {
            // Log step "(cached)".
            Flow.step(LOGGER, "Serviceability {}: {} (cached)", key, cached.status());
            // Return it - no partner call.
            return cached;
        }
        // Miss: timer.
        long start = System.nanoTime();
        // Fresh answer.
        Serviceability fresh;
        // Nested step around the partner call.
        try (Flow.Scope s = Flow.begin(LOGGER, "Serviceability {}: not cached, calling partner API", key)) {
            // Run the client's lambda (HTTP call + parsing).
            fresh = loader.get();
        }
        // Log the answer, error code, time and whether it is cached.
        Flow.step(LOGGER, "Serviceability {}: {}{} ({} ms, {})", key, fresh.status(),
                fresh.errorCode() == null ? "" : " [" + fresh.errorCode().code() + "]",
                (System.nanoTime() - start) / 1_000_000, fresh.cacheable() ? "cached" : "not cached - retried next request");
        // Definite answer (SERVICEABLE / NOT_SERVICEABLE)?
        if (fresh.cacheable()) {
            // Cache it; UNKNOWN is retried next time.
            cache.put(key, fresh);
        }
        // Return.
        return fresh;
    }

    // Clear (rate-card admin eviction).
    public void evictAll() {
        // Invalidate all.
        cache.invalidateAll();
    }
}
