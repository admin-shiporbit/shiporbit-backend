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

@Component
public class DelhiveryTokenFetcher implements TokenFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(DelhiveryTokenFetcher.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient delhiveryClient;
    private final DelhiveryConfigProperties delhiveryConfigProperties;

    public DelhiveryTokenFetcher(RestClient delhiveryClient,
                                 DelhiveryConfigProperties delhiveryConfigProperties) {
        this.delhiveryClient = delhiveryClient;
        this.delhiveryConfigProperties = delhiveryConfigProperties;
    }

    @Override
    public TokenResult fetch() {
        LOGGER.debug("Fetching a new Delhivery auth token");
        Map<String, Object> body = Map.of("username", delhiveryConfigProperties.username(),
                "password", delhiveryConfigProperties.password());
        String url = delhiveryConfigProperties.routerUrl() + delhiveryConfigProperties.loginEndpoint();
        LOGGER.debug("Outgoing Delhivery login request:\n{}", CurlLogger.toCurl("POST", url,
                Map.of("Content-Type", "application/json"),
                CurlLogger.redact(body, "username", "password")));
        Map response;
        try {
            response = delhiveryClient.post()
                    .uri(delhiveryConfigProperties.loginEndpoint())
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (HttpStatusCodeException e) {
            // Delhivery responded with a 4xx/5xx - surface their actual error message
            throw PartnerApiException.from(e, "Delhivery");
        } catch (RestClientException e) {
            // Couldn't even reach Delhivery (network/timeout)
            throw new PartnerApiException("Unable to connect to Delhivery login API", e, HttpStatus.BAD_GATEWAY.value());
        }

        if (response == null
                || !(response.get("data") instanceof Map<?, ?> data)
                || data.get("jwt") == null) {
            throw new PartnerApiException("Login token missing from the Delhivery response", HttpStatus.BAD_GATEWAY.value());
        }
        // Delhivery's jwt field is the raw JWT string itself (not a nested object), so
        // wrap it into the Map<String,String> shape TokenResult.token() now requires
        // (DelhiveryPartnerClient reads it back out via .get("token")).
        String token = data.get("jwt").toString();
        Map<String, String> tokenMap = Map.of("token", token);
        Instant expiresAt = extractExpiry(token);
        LOGGER.debug("Fetched Delhivery auth token, expires at {}", expiresAt);
        return new TokenResult(tokenMap, expiresAt);
    }

    private Instant extractExpiry(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length != 3) {
                throw new PartnerApiException("Malformed JWT returned by Delhivery", HttpStatus.BAD_GATEWAY.value());
            }
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> claims = OBJECT_MAPPER.readValue(payloadJson, Map.class);
            Object exp = claims.get("exp");
            if (!(exp instanceof Number expNumber)) {
                throw new PartnerApiException("JWT missing 'exp' claim", HttpStatus.BAD_GATEWAY.value());
            }
            return Instant.ofEpochSecond(expNumber.longValue());
        } catch (PartnerApiException e) {
            throw e;
        } catch (Exception e) {
            throw new PartnerApiException("Failed to parse JWT expiry", e, HttpStatus.BAD_GATEWAY.value());
        }
    }
}
