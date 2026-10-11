package com.shiporbit.backend.rate.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * In-memory (Caffeine) caches for rate-card reads used on every quote. All cached values are
 * immutable (snapshots, records, Map.copyOf), so they are safe to share across threads.
 * <ul>
 *   <li>{@link #SERVICES}: enabled services + ACTIVE card snapshot per pickup date (5 queries saved).</li>
 *   <li>{@link #PARTNER_NAMES}: partner id -> name (1 query saved).</li>
 *   <li>{@link #LANES}: pincode flags + zone keys per origin/destination (2 queries saved).</li>
 * </ul>
 * Entries expire after shiporbit.rates.cache-ttl-minutes (default 10) as a safety net for direct DB edits;
 * app-side changes (import, activation) call RateCardCacheService.evictAll() so new rates apply at once.
 * Single-instance cache: with several app instances each has its own copy (fine until we scale out).
 */
@Configuration
@EnableCaching
public class RateCacheConfig {

    public static final String SERVICES = "rateCardServices";
    public static final String PARTNER_NAMES = "ratePartnerNames";
    public static final String LANES = "rateLanes";

    @Bean
    public CacheManager cacheManager(@Value("${shiporbit.rates.cache-ttl-minutes:10}") long ttlMinutes,
                                     @Value("${shiporbit.rates.lane-cache-size:10000}") long laneCacheSize) {
        Duration ttl = Duration.ofMinutes(ttlMinutes);
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache(SERVICES, Caffeine.newBuilder()
                .maximumSize(60)                 // one entry per pickup date
                .expireAfterWrite(ttl).recordStats().build());
        manager.registerCustomCache(PARTNER_NAMES, Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(ttl).recordStats().build());
        manager.registerCustomCache(LANES, Caffeine.newBuilder()
                .maximumSize(laneCacheSize)      // one entry per origin/destination pair
                .expireAfterWrite(ttl).recordStats().build());
        return manager;
    }
}
