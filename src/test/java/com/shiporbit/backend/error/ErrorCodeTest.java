package com.shiporbit.backend.error;

import com.shiporbit.backend.exception.PartnerApiException;
import com.shiporbit.backend.rate.core.QuoteStatus;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ErrorCodeTest {

    @Test
    void codesAreUniqueAndEveryQuoteStatusHasOne() {
        Set<String> seen = new HashSet<>();
        Arrays.stream(ErrorCode.values()).forEach(c -> assertEquals(true, seen.add(c.code()), "duplicate " + c.code()));
        for (QuoteStatus s : QuoteStatus.values()) {
            if (s != QuoteStatus.OK) assertNotNull(ErrorCode.forQuoteStatus(s), s.name());
        }
    }

    @Test
    void partnerExceptionClassification() {
        assertEquals(ErrorCode.PARTNER_AUTH_FAILED, new PartnerApiException("x", 401).errorCode());
        assertEquals(ErrorCode.PARTNER_REJECTED_REQUEST, new PartnerApiException("x", 422).errorCode());
        assertEquals(ErrorCode.PARTNER_NOT_CONFIGURED, new PartnerApiException("x", 503).errorCode());
        assertEquals(ErrorCode.PARTNER_BAD_RESPONSE, new PartnerApiException("x", 502).errorCode());
        assertEquals(ErrorCode.PARTNER_TIMEOUT,
                new PartnerApiException("x", new RuntimeException(new SocketTimeoutException()), 502).errorCode());
        assertEquals(ErrorCode.PARTNER_UNAVAILABLE, PartnerApiException.codeForStatus(500, true));
    }
}
