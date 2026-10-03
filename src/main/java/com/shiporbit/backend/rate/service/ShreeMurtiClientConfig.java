package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.rate.routing.DelhiveryConfigProperties;
import com.shiporbit.backend.rate.routing.ShreeMurtiConfigProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ShreeMurtiClientConfig {

    @Bean
    public RestClient shreeMurtiClient(RestClient.Builder restClient,
                                      ShreeMurtiConfigProperties shreeMurtiConfigProperties) {
        return restClient.baseUrl(shreeMurtiConfigProperties.routerUrl()).build();
    }
}
