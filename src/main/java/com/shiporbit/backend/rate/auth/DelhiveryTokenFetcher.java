package com.shiporbit.backend.rate.auth;

import com.shiporbit.backend.exception.DelhiveryApiException;
import com.shiporbit.backend.rate.routing.DelhiveryConfigProperties;
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
        Map response;
        try {
            response = delhiveryClient.post()
                    .uri(delhiveryConfigProperties.loginEndpoint())
                    .header("Content-Type", "application/json")
                    .body(Map.of("username", delhiveryConfigProperties.username(),
                            "password", delhiveryConfigProperties.password()))
                    .retrieve()
                    .body(Map.class);
        } catch (HttpStatusCodeException e) {
            // Delhivery responded with a 4xx/5xx - surface their actual error message
            throw DelhiveryApiException.from(e);
        } catch (RestClientException e) {
            // Couldn't even reach Delhivery (network/timeout)
            throw new DelhiveryApiException("Unable to connect to Delhivery login API", e, HttpStatus.BAD_GATEWAY.value());
        }

        if (response == null
                || !(response.get("data") instanceof Map<?, ?> data)
                || data.get("jwt") == null) {
            throw new DelhiveryApiException("Login token missing from the Delhivery response", HttpStatus.BAD_GATEWAY.value());
        }
        String token = data.get("jwt").toString();
        return new TokenResult(token, extractExpiry(token));
    }

    private Instant extractExpiry(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length != 3) {
                throw new DelhiveryApiException("Malformed JWT returned by Delhivery", HttpStatus.BAD_GATEWAY.value());
            }
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> claims = OBJECT_MAPPER.readValue(payloadJson, Map.class);
            Object exp = claims.get("exp");
            if (!(exp instanceof Number expNumber)) {
                throw new DelhiveryApiException("JWT missing 'exp' claim", HttpStatus.BAD_GATEWAY.value());
            }
            return Instant.ofEpochSecond(expNumber.longValue());
        } catch (DelhiveryApiException e) {
            throw e;
        } catch (Exception e) {
            throw new DelhiveryApiException("Failed to parse JWT expiry", e, HttpStatus.BAD_GATEWAY.value());
        }
    }
}
