package com.shiporbit.backend.rate.auth;

import com.shiporbit.backend.exception.PartnerApiException;
import com.shiporbit.backend.rate.routing.UpsConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * UPS OAuth2 client-credentials token:
 * POST /security/v1/oauth/token, Basic auth (clientId:clientSecret),
 * form body grant_type=client_credentials.
 * Response: {access_token, token_type, expires_in (seconds, sent as a string), ...}.
 */
@Component
public class UpsTokenFetcher implements TokenFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpsTokenFetcher.class);
    // Refresh a minute early so a token never expires mid-request.
    private static final long EXPIRY_SAFETY_SECONDS = 60;

    private final RestClient upsClient;
    private final UpsConfigProperties upsConfigProperties;

    public UpsTokenFetcher(RestClient upsClient, UpsConfigProperties upsConfigProperties) {
        this.upsClient = upsClient;
        this.upsConfigProperties = upsConfigProperties;
    }

    @Override
    public TokenResult fetch() {
        LOGGER.debug("Fetching a new UPS OAuth token");
        String basicAuth = Base64.getEncoder().encodeToString(
                (upsConfigProperties.clientId() + ":" + upsConfigProperties.clientSecret())
                        .getBytes(StandardCharsets.UTF_8));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        Map response;
        try {
            var request = upsClient.post()
                    .uri(upsConfigProperties.tokenEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Basic " + basicAuth);
            if (upsConfigProperties.hasAccountNumber()) {
                request = request.header("x-merchant-id", upsConfigProperties.accountNumber());
            }
            response = request.body(form).retrieve().body(Map.class);
        } catch (HttpStatusCodeException e) {
            throw PartnerApiException.from(e, "UPS");
        } catch (RestClientException e) {
            throw new PartnerApiException("Unable to connect to UPS OAuth API", e, HttpStatus.BAD_GATEWAY.value());
        }

        if (response == null || response.get("access_token") == null || response.get("expires_in") == null) {
            throw new PartnerApiException("Access token missing from the UPS response", HttpStatus.BAD_GATEWAY.value());
        }

        long expiresInSeconds = Long.parseLong(response.get("expires_in").toString());
        Instant expiresAt = Instant.now().plusSeconds(Math.max(0, expiresInSeconds - EXPIRY_SAFETY_SECONDS));
        LOGGER.debug("Fetched UPS OAuth token, expires at {}", expiresAt);
        return new TokenResult(Map.of("token", response.get("access_token").toString()), expiresAt);
    }
}
