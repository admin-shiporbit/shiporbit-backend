package com.shiporbit.backend.payment.service;

import com.shiporbit.backend.payment.dto.PaymentInitiationRequest;
import com.shiporbit.backend.payment.dto.PaymentInitiationResult;
import com.shiporbit.backend.payment.dto.PaymentVerificationResult;

import java.util.Map;

/** Strategy interface so a second gateway can be added later without touching WalletService. */
public interface PaymentGatewayClient {
    String gatewayCode();

    PaymentInitiationResult initiate(PaymentInitiationRequest request);

    /** Verifies the callback's hash before the caller trusts any of these fields. */
    PaymentVerificationResult verifyCallback(Map<String, String> callbackParams);
}
