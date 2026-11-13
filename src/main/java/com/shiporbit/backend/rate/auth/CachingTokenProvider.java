package com.shiporbit.backend.rate.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

// Per-partner token cache (one instance per partner client, not a Spring bean).
public class CachingTokenProvider {

    // Logger.
    private static final Logger LOGGER = LoggerFactory.getLogger(CachingTokenProvider.class);

    // The partner's login call.
    private final TokenFetcher tokenFetcher;
    // Last token + expiry (volatile: visible across threads).
    private volatile TokenResult cache;

    // Built by the partner client.
    public CachingTokenProvider(TokenFetcher tokenFetcher) {
        // Store fetcher.
        this.tokenFetcher = tokenFetcher;
    }

    // synchronized: parallel quotes never log in twice at the same time.
    public synchronized Map<String,String> getValidToken() {
        // No token yet, or expired?
        if(cache == null || Instant.now().isAfter(cache.expiresAt())){
            // Fetcher class name for logs.
            String fetcher = tokenFetcher.getClass().getSimpleName();
            // Timer.
            long start = System.nanoTime();
            // Log step: logging in.
            com.shiporbit.backend.logging.Flow.step(LOGGER, "Partner token: none cached, logging in ({})", fetcher);
            // Call the partner login API (DelhiveryTokenFetcher / ShreeMurtiTokenFetcher).
            cache = tokenFetcher.fetch();
            // Log step: token received and its expiry.
            com.shiporbit.backend.logging.Flow.step(LOGGER, "Partner token: received ({}), valid until {}, {} ms",
                    fetcher, cache.expiresAt(), (System.nanoTime() - start) / 1_000_000);
        // Token still valid:
        } else {
            // debug log, reuse it.
            LOGGER.debug("Reusing cached token for {}, valid until {}", tokenFetcher.getClass().getSimpleName(), cache.expiresAt());
        }
        // Token map (Delhivery: "token"; Shree Maruti: "id_token" + "tenantId").
        return cache.token();
    }
}
