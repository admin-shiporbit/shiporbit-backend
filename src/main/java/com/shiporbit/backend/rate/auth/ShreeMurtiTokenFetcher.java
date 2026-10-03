package com.shiporbit.backend.rate.auth;

import com.shiporbit.backend.exception.PartnerApiException;
import com.shiporbit.backend.rate.routing.ShreeMurtiConfigProperties;
import com.shiporbit.backend.rate.util.CurlLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Confirmed via curl against ShreeMurti's real login and refresh-token responses:
 * both return "id_token" (the JWT itself, as a top-level string - NOT nested under
 * anything), "refresh_token" (opaque string, snake_case key), and "expires_in" (seconds
 * from now, so no need to decode the JWT's own "exp" claim at all).
 *
 * Still NOT verified - only success responses have been seen so far:
 * - What ShreeMurti actually returns when a refresh token is invalid/expired (401 is
 *   assumed below in fetch() - confirm by deliberately calling refresh with a bad token).
 * - The login request body's field names (userName/password/signinType) - never
 *   confirmed directly, only guessed. The refresh request body below was switched to
 *   snake_case (user_id/refresh_token) to match the response's consistent snake_case
 *   convention (user_id, tenant_id, etc.), but that's inference, not a confirmed request
 *   contract - curl the refresh endpoint directly to be sure.
 *
 * Uses the shared PartnerApiException (generic across every rate partner, not
 * Delhivery-specific despite its original name) for all failures here.
 */
@Component
public class ShreeMurtiTokenFetcher implements TokenFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShreeMurtiTokenFetcher.class);

    private volatile String refreshToken = null;

    private final RestClient shreeMurtiClient;
    private final ShreeMurtiConfigProperties shreeMurtiConfigProperties;

    public ShreeMurtiTokenFetcher(RestClient shreeMurtiClient,
                                   ShreeMurtiConfigProperties shreeMurtiConfigProperties) {
        this.shreeMurtiClient = shreeMurtiClient;
        this.shreeMurtiConfigProperties = shreeMurtiConfigProperties;
    }

    @Override
    public TokenResult fetch() {
        if (refreshToken == null) {
            return doLogin();
        }

        try {
            return doRefresh(refreshToken);
        } catch (HttpStatusCodeException e) {
            // TODO verify: is 401 actually what ShreeMurti returns for an invalid/expired
            // refresh token? Only success responses have been confirmed so far.
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                LOGGER.info("ShreeMurti refresh token rejected ({}), falling back to full login",
                        e.getStatusCode());
                return doLogin();
            }
            throw new PartnerApiException(
                    "ShreeMurti refresh call failed (" + e.getStatusCode() + "): " + e.getResponseBodyAsString(),
                    e, e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new PartnerApiException(
                    "Unable to connect to ShreeMurti refresh-token API", e, HttpStatus.BAD_GATEWAY.value());
        }
    }

    private TokenResult doLogin() {
        LOGGER.debug("Performing a full ShreeMurti login");
        Map<String, Object> body = new HashMap<>();
        body.put("username", shreeMurtiConfigProperties.userName());
        body.put("password", shreeMurtiConfigProperties.password());
        body.put("signinType", shreeMurtiConfigProperties.signinType());

        String url = shreeMurtiConfigProperties.routerUrl() + shreeMurtiConfigProperties.loginEndPoint();
        LOGGER.debug("Outgoing ShreeMurti login request:\n{}", CurlLogger.toCurl("POST", url,
                Map.of("Content-Type", "application/json"),
                CurlLogger.redact(body, "username", "password")));
        Map response;
        try {
            response = shreeMurtiClient.post()
                    .uri(shreeMurtiConfigProperties.loginEndPoint())
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (HttpStatusCodeException e) {
            throw new PartnerApiException(
                    "ShreeMurti login failed (" + e.getStatusCode() + "): " + e.getResponseBodyAsString(),
                    e, e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new PartnerApiException(
                    "Unable to connect to ShreeMurti login API", e, HttpStatus.BAD_GATEWAY.value());
        }

        return parseAndStore(response);
    }

    // Deliberately does not catch HttpStatusCodeException/RestClientException here -
    // fetch() needs to see the raw exception to decide whether to fall back to login
    // (invalid refresh token) or propagate (any other failure).
    private TokenResult doRefresh(String currentRefreshToken) {
        LOGGER.debug("Refreshing ShreeMurti token via refresh_token");
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", shreeMurtiConfigProperties.userId());
        body.put("refresh_token", currentRefreshToken);

        String url = shreeMurtiConfigProperties.routerUrl() + shreeMurtiConfigProperties.refreshTokenEndPoint();
        LOGGER.debug("Outgoing ShreeMurti refresh request:\n{}", CurlLogger.toCurl("POST", url,
                Map.of("Content-Type", "application/json"),
                CurlLogger.redact(body, "refresh_token")));
        Map response = shreeMurtiClient.post()
                .uri(shreeMurtiConfigProperties.refreshTokenEndPoint())
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(Map.class);

        return parseAndStore(response);
    }

    private TokenResult parseAndStore(Map response) {
        if (response == null
                || response.get("id_token") == null
                || response.get("expires_in") == null) {
            throw new PartnerApiException(
                    "Login token missing from the ShreeMurti response", HttpStatus.BAD_GATEWAY.value());
        }

        String token = response.get("id_token").toString();
        String tenantId = response.get("tenant_id").toString();
        long expiresInSeconds = ((Number) response.get("expires_in")).longValue();
        Map<String,String> tokenMap = new HashMap<>();
        Object newRefreshToken = response.get("refresh_token");
        if (newRefreshToken != null) {
            this.refreshToken = newRefreshToken.toString();
        }
        tokenMap.put("id_token", token);
        tokenMap.put("tenantId", tenantId);

        Instant expiresAt = Instant.now().plusSeconds(expiresInSeconds);
        LOGGER.debug("Fetched ShreeMurti auth token, expires at {}", expiresAt);
        return new TokenResult(tokenMap, expiresAt);
    }
}
