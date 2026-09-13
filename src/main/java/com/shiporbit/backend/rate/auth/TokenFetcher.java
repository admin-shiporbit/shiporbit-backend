package com.shiporbit.backend.rate.auth;

@FunctionalInterface
public interface TokenFetcher {
    TokenResult fetch();
}
