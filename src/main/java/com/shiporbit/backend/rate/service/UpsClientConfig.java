package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.rate.routing.UpsConfigProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class UpsClientConfig {

    @Bean
    public RestClient upsClient(RestClient.Builder restClient, UpsConfigProperties upsConfigProperties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(upsConfigProperties.connectTimeoutSeconds()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(upsConfigProperties.readTimeoutSeconds()));
        return restClient
                .baseUrl(upsConfigProperties.routerUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
