package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.rate.routing.DelhiveryConfigProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class DelhiveryClientConfig {

    @Bean
    public RestClient delhiveryClient(RestClient.Builder restClient,
                                      DelhiveryConfigProperties delhiveryConfigProperties) {
        return restClient.baseUrl(delhiveryConfigProperties.routerUrl()).build();
    }
}
