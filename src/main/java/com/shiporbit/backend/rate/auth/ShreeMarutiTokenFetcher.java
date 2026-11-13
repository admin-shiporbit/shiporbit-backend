package com.shiporbit.backend.rate.auth;

import com.shiporbit.backend.exception.PartnerApiException;
import com.shiporbit.backend.rate.routing.ShreeMarutiConfigProperties;
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
// Spring bean; wrapped by ShreeMurtiPartnerClient's CachingTokenProvider.
@Component
// Shree Maruti login / refresh.
public class ShreeMarutiTokenFetcher implements TokenFetcher {

    // Logger.
    private static final Logger LOGGER = LoggerFactory.getLogger(ShreeMarutiTokenFetcher.class);

    // Last refresh token (null until the first login).
    private volatile String refreshToken = null;

    // RestClient with Shree Maruti base URL.
    private final RestClient shreeMarutiClient;
    // Credentials + endpoints.
    private final ShreeMarutiConfigProperties shreeMarutiConfigProperties;

    // Constructor injection.
    public ShreeMarutiTokenFetcher(RestClient shreeMarutiClient,
                                   ShreeMarutiConfigProperties shreeMarutiConfigProperties) {
        // Store.
        this.shreeMarutiClient = shreeMarutiClient;
        // Store.
        this.shreeMarutiConfigProperties = shreeMarutiConfigProperties;
    }

    @Override
    // Called only when no valid token is cached.
    public TokenResult fetch() {
        // Never logged in?
        if (refreshToken == null) {
            // Full login.
            return doLogin();
        }

        // Try refresh first:
        try {
            // refresh call.
            return doRefresh(refreshToken);
        // HTTP error:
        } catch (HttpStatusCodeException e) {
            // TODO verify: is 401 actually what ShreeMurti returns for an invalid/expired
            // refresh token? Only success responses have been confirmed so far.
            // 401 = refresh token rejected?
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                // log,
                LOGGER.info("ShreeMurti refresh token rejected ({}), falling back to full login",
                        e.getStatusCode());
                // fall back to full login.
                return doLogin();
            }
            // Other HTTP error -> PartnerApiException.
            throw new PartnerApiException(
                    "ShreeMurti refresh call failed (" + e.getStatusCode() + "): " + e.getResponseBodyAsString(),
                    e, e.getStatusCode().value());
        // Unreachable:
        } catch (RestClientException e) {
            // -> PartnerApiException 502.
            throw new PartnerApiException(
                    "Unable to connect to ShreeMurti refresh-token API", e, HttpStatus.BAD_GATEWAY.value());
        }
    }

    // Full login.
    private TokenResult doLogin() {
        // Debug log.
        LOGGER.debug("Performing a full ShreeMurti login");
        // Body:
        Map<String, Object> body = new HashMap<>();
        // username,
        body.put("username", shreeMarutiConfigProperties.userName());
        // password,
        body.put("password", shreeMarutiConfigProperties.password());
        // signinType.
        body.put("signinType", shreeMarutiConfigProperties.signinType());

        // Full URL (debug only).
        String url = shreeMarutiConfigProperties.routerUrl() + shreeMarutiConfigProperties.loginEndPoint();
        // Debug curl with credentials redacted.
        LOGGER.debug("Outgoing ShreeMurti login request:\n{}", CurlLogger.toCurl("POST", url,
                Map.of("Content-Type", "application/json"),
                CurlLogger.redact(body, "username", "password")));
        // Raw answer.
        Map response;
        // Try:
        try {
            // POST
            response = shreeMarutiClient.post()
                    // login endpoint,
                    .uri(shreeMarutiConfigProperties.loginEndPoint())
                    // JSON,
                    .header("Content-Type", "application/json")
                    // body,
                    .body(body)
                    // send,
                    .retrieve()
                    // read as Map.
                    .body(Map.class);
        // HTTP error:
        } catch (HttpStatusCodeException e) {
            // -> PartnerApiException.
            throw new PartnerApiException(
                    "ShreeMurti login failed (" + e.getStatusCode() + "): " + e.getResponseBodyAsString(),
                    e, e.getStatusCode().value());
        // Unreachable:
        } catch (RestClientException e) {
            // -> PartnerApiException 502.
            throw new PartnerApiException(
                    "Unable to connect to ShreeMurti login API", e, HttpStatus.BAD_GATEWAY.value());
        }

        // Read token + store refresh token.
        return parseAndStore(response);
    }

    // Deliberately does not catch HttpStatusCodeException/RestClientException here -
    // fetch() needs to see the raw exception to decide whether to fall back to login
    // (invalid refresh token) or propagate (any other failure).
    // Refresh with the stored refresh token.
    private TokenResult doRefresh(String currentRefreshToken) {
        // Debug log.
        LOGGER.debug("Refreshing ShreeMurti token via refresh_token");
        // Body:
        Map<String, Object> body = new HashMap<>();
        // user_id,
        body.put("user_id", shreeMarutiConfigProperties.userId());
        // refresh_token.
        body.put("refresh_token", currentRefreshToken);

        // Full URL (debug only).
        String url = shreeMarutiConfigProperties.routerUrl() + shreeMarutiConfigProperties.refreshTokenEndPoint();
        // Debug curl with the refresh token redacted.
        LOGGER.debug("Outgoing ShreeMurti refresh request:\n{}", CurlLogger.toCurl("POST", url,
                Map.of("Content-Type", "application/json"),
                CurlLogger.redact(body, "refresh_token")));
        // POST (exceptions go to fetch(), which decides login vs fail):
        Map response = shreeMarutiClient.post()
                // refresh endpoint,
                .uri(shreeMarutiConfigProperties.refreshTokenEndPoint())
                // JSON,
                .header("Content-Type", "application/json")
                // body,
                .body(body)
                // send,
                .retrieve()
                // read as Map.
                .body(Map.class);

        // Read token + store refresh token.
        return parseAndStore(response);
    }

    // Read the login / refresh answer.
    private TokenResult parseAndStore(Map response) {
        // No response,
        if (response == null
                // no id_token,
                || response.get("id_token") == null
                // or no expires_in?
                || response.get("expires_in") == null) {
            // -> PartnerApiException 502.
            throw new PartnerApiException(
                    "Login token missing from the ShreeMurti response", HttpStatus.BAD_GATEWAY.value());
        }

        // JWT.
        String token = response.get("id_token").toString();
        // Tenant id (sent as header tenantid). Note: not null-checked above - a missing tenant_id gives a NullPointerException here.
        String tenantId = response.get("tenant_id").toString();
        // Lifetime in seconds.
        long expiresInSeconds = ((Number) response.get("expires_in")).longValue();
        // Token map.
        Map<String,String> tokenMap = new HashMap<>();
        // New refresh token?
        Object newRefreshToken = response.get("refresh_token");
        // Present?
        if (newRefreshToken != null) {
            // Keep it for the next refresh.
            this.refreshToken = newRefreshToken.toString();
        }
        // id_token,
        tokenMap.put("id_token", token);
        // tenantId.
        tokenMap.put("tenantId", tenantId);

        // Expiry = now + expires_in.
        Instant expiresAt = Instant.now().plusSeconds(expiresInSeconds);
        // Debug log.
        LOGGER.debug("Fetched ShreeMurti auth token, expires at {}", expiresAt);
        // Token + expiry for the cache.
        return new TokenResult(tokenMap, expiresAt);
    }
}
