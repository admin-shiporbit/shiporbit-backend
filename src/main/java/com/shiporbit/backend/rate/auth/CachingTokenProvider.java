package com.shiporbit.backend.rate.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

public class CachingTokenProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(CachingTokenProvider.class);

    private final TokenFetcher tokenFetcher;
    private volatile TokenResult cache;

    public CachingTokenProvider(TokenFetcher tokenFetcher) {
        this.tokenFetcher = tokenFetcher;
    }

    public synchronized Map<String,String> getValidToken() {
        if(cache == null || Instant.now().isAfter(cache.expiresAt())){
            LOGGER.debug("Token cache miss/expired for {}, fetching a new token", tokenFetcher.getClass().getSimpleName());
            cache = tokenFetcher.fetch();
        } else {
            LOGGER.debug("Reusing cached token for {}, valid until {}", tokenFetcher.getClass().getSimpleName(), cache.expiresAt());
        }
        return cache.token();
    }
}
