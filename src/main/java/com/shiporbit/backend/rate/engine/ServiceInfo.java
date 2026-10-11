package com.shiporbit.backend.rate.engine;

import com.shiporbit.backend.rate.entity.PricingModel;
import com.shiporbit.backend.rate.entity.ServiceMode;

/** Identity of one partner service (SO_RATE_SERVICE + partner name). */
public record ServiceInfo(Long serviceId, Integer partnerId, String partnerName, String serviceCode,
                          PricingModel pricingModel, ServiceMode mode) { }
