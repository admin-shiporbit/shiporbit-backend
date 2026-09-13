package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.dto.response.RateResponse;

import java.util.Map;

public interface RateAggregatorService {

    RateResponse getRate(String partnerCode, RequestParamRecord request);

    Map<String, RateResponse> compareRates(RequestParamRecord request);

    void validateRequest(RequestParamRecord request);
}
