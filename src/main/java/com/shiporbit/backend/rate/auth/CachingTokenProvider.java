package com.shiporbit.backend.rate.auth;

import java.time.Instant;

public class CachingTokenProvider {
    private final TokenFetcher tokenFetcher;
    private volatile TokenResult cache;

    public CachingTokenProvider(TokenFetcher tokenFetcher) {
        this.tokenFetcher = tokenFetcher;
    }

    public synchronized String getValidToken() {
        if(cache == null || Instant.now().isAfter(cache.expiresAt())){
            cache = tokenFetcher.fetch();
        }
        return cache.token();
    }
}
