package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.dto.response.RateResponse;

import java.math.BigDecimal;

public class MarkupPartnerClient implements DeliveryPartnerClient {
    private static final BigDecimal MARKUP = new BigDecimal("1.10");
    private final DeliveryPartnerClient delegate;

    public MarkupPartnerClient(DeliveryPartnerClient delegate) {
        this.delegate = delegate;
    }

    public String partnerCode() { return delegate.partnerCode(); }
    public String partnerName() { return delegate.partnerName(); }


    public boolean isServiceable(RequestParamRecord r) { return delegate.isServiceable(r); }

    public RateResponse getRate(RequestParamRecord r) {
        RateResponse rate = delegate.getRate(r);
        return new RateResponse(
                rate.partnerCode(), rate.partnerName(),
                rate.baseFreight().multiply(MARKUP),
                rate.fuelHike().multiply(MARKUP),
                rate.surcharge().multiply(MARKUP),
                rate.insuranceRov().multiply(MARKUP),
                rate.odaCharge().multiply(MARKUP),
                rate.handlingCharges().multiply(MARKUP),
                rate.gst().multiply(MARKUP),
                rate.finalFreight().multiply(MARKUP)
        );
    }
}