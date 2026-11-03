package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.rate.routing.ShreeMarutiConfigProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ShreeMurtiClientConfig {

    @Bean
    public RestClient shreeMurtiClient(RestClient.Builder restClient,
                                      ShreeMarutiConfigProperties shreeMarutiConfigProperties) {
        return restClient.baseUrl(shreeMarutiConfigProperties.routerUrl()).build();
    }
}
