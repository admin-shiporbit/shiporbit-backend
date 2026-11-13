package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.rate.routing.ShreeMarutiConfigProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ShreeMarutiClientConfig {

    @Bean
    public RestClient shreeMarutiClient(RestClient.Builder restClient,
                                      ShreeMarutiConfigProperties shreeMarutiConfigProperties) {
        return restClient.baseUrl(shreeMarutiConfigProperties.routerUrl()).build();
    }
}
