package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.dto.response.RateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

public class MarkupPartnerClient implements DeliveryPartnerClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarkupPartnerClient.class);
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
        RateResponse markedUp = new RateResponse(
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
        LOGGER.debug("Applied {}x markup to {}: {} -> {}", MARKUP, rate.partnerCode(), rate.finalFreight(), markedUp.finalFreight());
        return markedUp;
    }
}