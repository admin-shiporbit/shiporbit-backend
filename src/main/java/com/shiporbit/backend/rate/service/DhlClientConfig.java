package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.rate.routing.DhlConfigProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Configuration
public class DhlClientConfig {

    // MyDHL uses static Basic auth (no token exchange), so it's set once on the client.
    @Bean
    public RestClient dhlClient(RestClient.Builder restClient, DhlConfigProperties dhlConfigProperties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(dhlConfigProperties.connectTimeoutSeconds()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(dhlConfigProperties.readTimeoutSeconds()));

        RestClient.Builder builder = restClient
                .baseUrl(dhlConfigProperties.routerUrl())
                .requestFactory(requestFactory);
        if (dhlConfigProperties.isConfigured()) {
            String basicAuth = Base64.getEncoder().encodeToString(
                    (dhlConfigProperties.apiKey() + ":" + dhlConfigProperties.apiSecret())
                            .getBytes(StandardCharsets.UTF_8));
            builder = builder.defaultHeader("Authorization", "Basic " + basicAuth);
        }
        return builder.build();
    }
}
