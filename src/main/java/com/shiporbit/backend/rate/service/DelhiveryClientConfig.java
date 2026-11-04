package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.rate.routing.DelhiveryB2cConfigProperties;
import com.shiporbit.backend.rate.routing.DelhiveryConfigProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class DelhiveryClientConfig {

    /** LTL / B2B freight API (shipments >= rate-aggregator.delhivery-b2c.max-weight-kg). */
    @Bean
    public RestClient delhiveryClient(RestClient.Builder restClient,
                                      DelhiveryConfigProperties delhiveryConfigProperties) {
        return restClient.clone().baseUrl(delhiveryConfigProperties.routerUrl()).build();
    }

    /** B2C express-parcel API (lighter shipments). Separate host and auth from LTL. */
    @Bean
    public RestClient delhiveryB2cClient(RestClient.Builder restClient,
                                         DelhiveryB2cConfigProperties delhiveryB2cConfigProperties) {
        return restClient.clone().baseUrl(delhiveryB2cConfigProperties.routerUrl()).build();
    }
}
