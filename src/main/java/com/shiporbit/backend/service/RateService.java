package com.shiporbit.backend.service;

import com.shiporbit.backend.dto.DeliveryPartnerResponse;
import com.shiporbit.backend.dto.RateQuoteRequest;
import com.shiporbit.backend.dto.RateQuoteResponse;

import java.util.List;

public interface RateService {

    List<DeliveryPartnerResponse> availablePartners();

    RateQuoteResponse quote(String partnerCode, RateQuoteRequest request);

    List<RateQuoteResponse> compareAll(RateQuoteRequest request);
}
