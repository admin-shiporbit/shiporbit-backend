package com.shiporbit.backend.rate.auth;

import com.shiporbit.backend.exception.PartnerApiException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Login answer shape as received from Ekart on 2026-11-09 (token value shortened). */
class EkartTokenFetcherTest {

    @Test
    void tokenAndExpiryFiveMinutesEarly() {
        Instant now = Instant.parse("2026-11-09T14:44:49Z");
        TokenResult t = EkartTokenFetcher.toTokenResult(
                Map.of("access_token", "eyJ.abc.def", "scope", "", "expires_in", 86400, "token_type", "Bearer"), now);
        assertEquals("eyJ.abc.def", t.token().get("token"));
        assertEquals(now.plusSeconds(86400 - 300), t.expiresAt());
    }

    @Test
    void missingTokenOrExpiryFails() {
        assertThrows(PartnerApiException.class, () -> EkartTokenFetcher.toTokenResult(Map.of("expires_in", 86400), Instant.now()));
        assertThrows(PartnerApiException.class, () -> EkartTokenFetcher.toTokenResult(Map.of("access_token", "x"), Instant.now()));
        assertThrows(PartnerApiException.class, () -> EkartTokenFetcher.toTokenResult(null, Instant.now()));
    }
}
