package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.dto.response.RateResponse;

public interface DeliveryPartnerClient {
    String partnerCode();
    String partnerName();
    boolean isServiceable(RequestParamRecord request);
    RateResponse getRate(RequestParamRecord request);
}
