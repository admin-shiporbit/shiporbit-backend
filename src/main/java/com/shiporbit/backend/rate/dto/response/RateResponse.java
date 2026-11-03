package com.shiporbit.backend.rate.dto.response;

import java.math.BigDecimal;

public record RateResponse(
        String partnerCode,
        String partnerName,
        BigDecimal baseFreight,
        BigDecimal fuelHike,
        BigDecimal surcharge,
        BigDecimal insuranceRov,
        BigDecimal odaCharge,
        BigDecimal handlingCharges,
        BigDecimal gst,
        BigDecimal finalFreight
) {

}

