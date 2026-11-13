package com.shiporbit.backend.rate.auth;

import com.shiporbit.backend.error.ErrorCode;
import com.shiporbit.backend.exception.PartnerApiException;
import com.shiporbit.backend.rate.routing.EkartConfigProperties;
import com.shiporbit.backend.rate.util.CurlLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ekart login: POST {router}/integrations/v2/auth/token/{clientId} with {username, password}
 * -> {access_token, expires_in (seconds, ~86400), token_type: Bearer}. Wrapped by EkartPartnerClient's
 * CachingTokenProvider, so it runs once per ~24 h (Ekart itself returns the same token for 24 h).
 */
@Component
public class EkartTokenFetcher implements TokenFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(EkartTokenFetcher.class);
    /** Renew this much before Ekart's expiry so no call goes out with a token about to die. */
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(5);

    private final RestClient ekartClient;
    private final EkartConfigProperties props;

    public EkartTokenFetcher(RestClient ekartClient, EkartConfigProperties props) {
        this.ekartClient = ekartClient;
        this.props = props;
    }

    @Override
    public TokenResult fetch() {
        if (!props.configured()) {
            throw new PartnerApiException(ErrorCode.PARTNER_NOT_CONFIGURED,
                    "Ekart credentials not set (EKART_USERNAME / EKART_PASSWORD / EKART_CLIENT_ID)", null,
                    HttpStatus.SERVICE_UNAVAILABLE.value());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", props.username());
        body.put("password", props.password());
        LOGGER.debug("Ekart login request:\n{}", CurlLogger.toCurl("POST",
                props.routerUrl() + props.loginEndpoint().replace("{clientId}", "<client-id>"),
                Map.of("Content-Type", "application/json"), CurlLogger.redact(body, "username", "password")));

        Map<?, ?> response;
        try {
            response = ekartClient.post()
                    .uri(props.loginEndpoint(), props.clientId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (HttpStatusCodeException e) {
            throw PartnerApiException.from(e, "Ekart login");
        } catch (RestClientException e) {
            throw new PartnerApiException("Unable to connect to Ekart login API", e, HttpStatus.BAD_GATEWAY.value());
        }
        return toTokenResult(response, Instant.now());
    }

    /** Login answer -> token map {"token": access_token} + expiry. Package-visible for tests. */
    static TokenResult toTokenResult(Map<?, ?> response, Instant now) {
        Object token = response == null ? null : response.get("access_token");
        Object expiresIn = response == null ? null : response.get("expires_in");
        long seconds;
        if (expiresIn instanceof Number n) {
            seconds = n.longValue();
        } else if (expiresIn instanceof String s && s.trim().matches("\\d+")) {
            seconds = Long.parseLong(s.trim());
        } else {
            seconds = -1;
        }
        if (!(token instanceof String t) || t.isBlank() || seconds <= 0) {
            throw new PartnerApiException(ErrorCode.PARTNER_BAD_RESPONSE,
                    "Ekart login: access_token / expires_in missing", null, HttpStatus.BAD_GATEWAY.value());
        }
        Instant expiresAt = now.plusSeconds(seconds).minus(EXPIRY_MARGIN);
        LOGGER.debug("Ekart token received, valid until {}", expiresAt);
        return new TokenResult(Map.of("token", t), expiresAt);
    }
}
