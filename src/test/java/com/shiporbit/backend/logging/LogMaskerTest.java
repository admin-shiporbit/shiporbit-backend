package com.shiporbit.backend.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogMaskerTest {

    @Test
    void masksSecrets() {
        assertEquals("--header 'Authorization: **** ****'",
                LogMasker.mask("--header 'Authorization: Bearer abcdef123456'"));
        assertEquals("Authorization: **** ****", LogMasker.mask("Authorization: Token a60d1234567890abcdef"));
        assertEquals("{\"username\":\"x\",\"password\":\"****\"}", LogMasker.mask("{\"username\":\"x\",\"password\":\"s3cr3t!\"}"));
        assertEquals("api-key=**** next", LogMasker.mask("api-key=k-123456 next"));
        assertEquals("jwt eyJ****", LogMasker.mask("jwt eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.sig_part-1"));
        assertEquals("id_token: ****", LogMasker.mask("id_token: abc.def"));
    }

    @Test
    void masksPersonalData() {
        assertEquals("user sa***@gmail.com", LogMasker.mask("user saurabh@gmail.com"));
        assertEquals("phone 98******10", LogMasker.mask("phone 9876543210"));
        assertEquals("card ****1111", LogMasker.mask("card 4111111111111111"));
    }

    @Test
    void keepsFlowData() {
        String line = "Quote: 110084 -> 560001, 1.2 kg, total 1841.74, partner id 13, Token cache miss for DelhiveryTokenFetcher";
        assertEquals(line, LogMasker.mask(line));
        assertFalse(LogMasker.mask("weight=200").contains("****"));
        assertTrue(LogMasker.mask("secret=abc").contains("****"));
        assertEquals("epoch 1792310813260 awb 12345678901234", LogMasker.mask("epoch 1792310813260 awb 12345678901234"));
        assertEquals("Auth: no Bearer token", LogMasker.mask("Auth: no Bearer token"));
    }
}
