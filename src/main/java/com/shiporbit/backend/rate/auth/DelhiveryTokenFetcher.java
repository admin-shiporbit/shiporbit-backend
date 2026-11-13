package com.shiporbit.backend.rate.auth;

import com.shiporbit.backend.exception.PartnerApiException;
import com.shiporbit.backend.rate.routing.DelhiveryConfigProperties;
import com.shiporbit.backend.rate.util.CurlLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

// Spring bean; wrapped by DelhiveryPartnerClient's CachingTokenProvider.
@Component
// Delhivery LTL login.
public class DelhiveryTokenFetcher implements TokenFetcher {

    // Logger.
    private static final Logger LOGGER = LoggerFactory.getLogger(DelhiveryTokenFetcher.class);
    // JSON parser for the JWT payload.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // RestClient with Delhivery LTL base URL.
    private final RestClient delhiveryClient;
    // Username, password, endpoints.
    private final DelhiveryConfigProperties delhiveryConfigProperties;

    // Constructor injection.
    public DelhiveryTokenFetcher(RestClient delhiveryClient,
                                 DelhiveryConfigProperties delhiveryConfigProperties) {
        // Store.
        this.delhiveryClient = delhiveryClient;
        // Store.
        this.delhiveryConfigProperties = delhiveryConfigProperties;
    }

    @Override
    // Called only when no valid token is cached.
    public TokenResult fetch() {
        // Debug log.
        LOGGER.debug("Fetching a new Delhivery auth token");
        // Login body: username,
        Map<String, Object> body = Map.of("username", delhiveryConfigProperties.username(),
                // password (from env / config).
                "password", delhiveryConfigProperties.password());
        // Full URL (debug only).
        String url = delhiveryConfigProperties.routerUrl() + delhiveryConfigProperties.loginEndpoint();
        // Debug curl with username / password redacted.
        LOGGER.debug("Outgoing Delhivery login request:\n{}", CurlLogger.toCurl("POST", url,
                Map.of("Content-Type", "application/json"),
                CurlLogger.redact(body, "username", "password")));
        // Raw answer.
        Map response;
        // Try:
        try {
            // POST
            response = delhiveryClient.post()
                    // login endpoint,
                    .uri(delhiveryConfigProperties.loginEndpoint())
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
            // Delhivery responded with a 4xx/5xx - surface their actual error message
            // -> PartnerApiException (bubbles up -> Unavailable for Delhivery).
            throw PartnerApiException.from(e, "Delhivery");
        // Unreachable:
        } catch (RestClientException e) {
            // Couldn't even reach Delhivery (network/timeout)
            // -> PartnerApiException 502.
            throw new PartnerApiException("Unable to connect to Delhivery login API", e, HttpStatus.BAD_GATEWAY.value());
        }

        // No response,
        if (response == null
                // no data object,
                || !(response.get("data") instanceof Map<?, ?> data)
                // or no jwt?
                || data.get("jwt") == null) {
            // -> PartnerApiException 502.
            throw new PartnerApiException("Login token missing from the Delhivery response", HttpStatus.BAD_GATEWAY.value());
        }
        // Delhivery's jwt field is the raw JWT string itself (not a nested object), so
        // wrap it into the Map<String,String> shape TokenResult.token() now requires
        // (DelhiveryPartnerClient reads it back out via .get("token")).
        // The JWT string.
        String token = data.get("jwt").toString();
        // Wrap as {"token": jwt}.
        Map<String, String> tokenMap = Map.of("token", token);
        // Expiry from the JWT's exp claim.
        Instant expiresAt = extractExpiry(token);
        // Debug log.
        LOGGER.debug("Fetched Delhivery auth token, expires at {}", expiresAt);
        // Token + expiry for the cache.
        return new TokenResult(tokenMap, expiresAt);
    }

    // Read exp from the JWT.
    private Instant extractExpiry(String jwt) {
        // Try:
        try {
            // header.payload.signature.
            String[] parts = jwt.split("\\.");
            // Not 3 parts?
            if (parts.length != 3) {
                // -> PartnerApiException.
                throw new PartnerApiException("Malformed JWT returned by Delhivery", HttpStatus.BAD_GATEWAY.value());
            }
            // Base64url-decode the payload.
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            // Parse JSON claims.
            Map<String, Object> claims = OBJECT_MAPPER.readValue(payloadJson, Map.class);
            // exp claim.
            Object exp = claims.get("exp");
            // Not a number?
            if (!(exp instanceof Number expNumber)) {
                // -> PartnerApiException.
                throw new PartnerApiException("JWT missing 'exp' claim", HttpStatus.BAD_GATEWAY.value());
            }
            // Seconds since epoch -> Instant.
            return Instant.ofEpochSecond(expNumber.longValue());
        // Our own exception:
        } catch (PartnerApiException e) {
            // rethrow.
            throw e;
        // Anything else (bad Base64 / JSON):
        } catch (Exception e) {
            // -> PartnerApiException.
            throw new PartnerApiException("Failed to parse JWT expiry", e, HttpStatus.BAD_GATEWAY.value());
        }
    }
}
