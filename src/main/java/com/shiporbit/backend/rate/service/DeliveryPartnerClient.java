package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.error.ErrorCode;
import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.dto.response.RateResponse;
import com.shiporbit.backend.rate.serviceability.Serviceability;

public interface DeliveryPartnerClient {
    String partnerCode();
    String partnerName();
    boolean isServiceable(RequestParamRecord request);
    RateResponse getRate(RequestParamRecord request);

    /**
     * Serviceability with a reason and an UNKNOWN state. Partners with a real serviceability API override
     * this (and make isServiceable() delegate to it); others keep the boolean isServiceable().
     */
    default Serviceability checkServiceability(RequestParamRecord request) {
        return isServiceable(request)
                ? Serviceability.yes("isServiceable() = true")
                : Serviceability.no(ErrorCode.NOT_SERVICEABLE, "Partner reports lane not serviceable");
    }
}
