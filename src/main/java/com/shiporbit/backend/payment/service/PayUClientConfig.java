package com.shiporbit.backend.payment.service;

import com.shiporbit.backend.payment.routing.PayUConfigProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PayUClientConfig {

    /**
     * Not yet called anywhere (see PayUPaymentGatewayClient's class comment) - reserved for the
     * verify_payment server-to-server reconciliation call once its request/response contract is
     * confirmed via curl, the same way DelhiveryClientConfig's bean predates the calls that use it.
     */
    @Bean
    public RestClient payUClient(RestClient.Builder restClient, PayUConfigProperties payUConfigProperties) {
        return restClient.baseUrl(payUConfigProperties.baseUrl()).build();
    }
}
