package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.dto.response.RateResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FakePartnerClient implements DeliveryPartnerClient{
    @Override
    public String partnerCode() {
        return "01";
    }

    @Override
    public String partnerName() {
        return "Fake";
    }

    @Override
    public boolean isServiceable(RequestParamRecord request) {
        return true;
    }

    @Override
    public RateResponse getRate(RequestParamRecord request) {
        return new RateResponse("01",
                "Fake",
                new BigDecimal("1000.10"),
                new BigDecimal("120.20"),
                new BigDecimal("13.0"),
                new BigDecimal("122.0"),
                new BigDecimal("0.0"),
                new BigDecimal("1"),
                new BigDecimal("111"),
                new BigDecimal(1111));
    }
}
