package com.shiporbit.backend.rate.dto.request;

import com.shiporbit.backend.dto.Dimension;

public record RequestParamRecord(
        Double weight,
        String sourcePinCode,
        String destinationPinCode,
        Dimension dimension,
        boolean chequePayment,
        String paymentMode,
        Double inventoryAmout,
        String freightMode,
        boolean isRovInsurance
) {
    public RequestParamRecord{
        chequePayment = defaultIfBlank(chequePayment, Boolean.FALSE);
        paymentMode = defaultIfBlank(paymentMode,"NA");
        inventoryAmout = defaultIfBlank(inventoryAmout,0.0);
        freightMode = defaultIfBlank(freightMode,"NA");
        isRovInsurance = defaultIfBlank(isRovInsurance,false);
    }

    private static <T>T defaultIfBlank(T value, T defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return (value == null ||  value.isBlank()? fallback : value);
    }
}
