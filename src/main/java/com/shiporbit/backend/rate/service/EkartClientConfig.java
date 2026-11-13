package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.rate.routing.EkartConfigProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** RestClient for Ekart Elite with connect / read timeouts (same pattern as UpsClientConfig). */
@Configuration
public class EkartClientConfig {

    @Bean
    public RestClient ekartClient(RestClient.Builder builder, EkartConfigProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.connectTimeoutSeconds()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(props.readTimeoutSeconds()));
        return builder.clone()
                .baseUrl(props.routerUrl() == null ? "" : props.routerUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
