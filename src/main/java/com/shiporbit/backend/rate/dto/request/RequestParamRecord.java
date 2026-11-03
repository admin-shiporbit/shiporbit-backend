package com.shiporbit.backend.rate.dto.request;

import com.shiporbit.backend.dto.Dimension;

import java.util.Map;

public record RequestParamRecord(
        Double weight,
        String sourcePinCode,
        String destinationPinCode,
        Dimension dimension,
        boolean chequePayment,
        String paymentMode,
        Double inventoryAmout,
        String freightMode,
        boolean isRovInsurance,
        Map<String, Object> partnerOptions
) {
    public RequestParamRecord{
        chequePayment = defaultIfBlank(chequePayment, Boolean.FALSE);
        paymentMode = defaultIfBlank(paymentMode,"NA");
        inventoryAmout = defaultIfBlank(inventoryAmout,0.0);
        freightMode = defaultIfBlank(freightMode,"NA");
        isRovInsurance = defaultIfBlank(isRovInsurance,false);
        // Extension point: anything genuinely specific to one partner (not just a
        // different arrangement of the fields above) goes here instead of growing this
        // record. Each partner's own buildRequestBody() reads only the keys it defines,
        // with a sensible default when a key is absent - so omitting a key never breaks
        // a request aimed at a different partner. Document which keys a partner expects
        // inside that partner's own buildRequestBody(), not here.
        partnerOptions = partnerOptions == null ? Map.of() : partnerOptions;
    }

    private static <T>T defaultIfBlank(T value, T defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return (value == null ||  value.isBlank()? fallback : value);
    }
}
