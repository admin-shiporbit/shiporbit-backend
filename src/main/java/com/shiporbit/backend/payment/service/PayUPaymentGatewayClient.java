package com.shiporbit.backend.payment.service;

import com.shiporbit.backend.exception.PaymentGatewayException;
import com.shiporbit.backend.payment.dto.PaymentInitiationRequest;
import com.shiporbit.backend.payment.dto.PaymentInitiationResult;
import com.shiporbit.backend.payment.dto.PaymentVerificationResult;
import com.shiporbit.backend.payment.routing.PayUConfigProperties;
import com.shiporbit.backend.payment.util.PayUHashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Trust boundary for PayU's hosted-checkout callback is the hash in verifyCallback() alone.
 * PayU also offers a verify_payment server-to-server reconciliation API as defense-in-depth
 * against a forged client-side postback; that call is NOT implemented yet (contract not
 * confirmed via curl) - see the handoff notes. Do not treat this class as fully hardened
 * until that reconciliation call exists.
 */
@Component
public class PayUPaymentGatewayClient implements PaymentGatewayClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayUPaymentGatewayClient.class);
    private static final String STATUS_SUCCESS = "success";

    private final PayUConfigProperties payUConfigProperties;

    public PayUPaymentGatewayClient(PayUConfigProperties payUConfigProperties) {
        this.payUConfigProperties = payUConfigProperties;
    }

    @Override
    public String gatewayCode() {
        return "PAYU";
    }

    @Override
    public PaymentInitiationResult initiate(PaymentInitiationRequest request) {
        String amount = request.amount().setScale(2, RoundingMode.HALF_UP).toPlainString();
        String productInfo = request.productInfo();
        String firstName = request.customerName();
        String email = request.customerEmail();

        String hash = PayUHashUtil.requestHash(
                payUConfigProperties.merchantKey(),
                request.referenceId(),
                amount,
                productInfo,
                firstName,
                email,
                null, null, null, null, null,
                payUConfigProperties.merchantSalt()
        );

        Map<String, String> formFields = new LinkedHashMap<>();
        formFields.put("key", payUConfigProperties.merchantKey());
        formFields.put("txnid", request.referenceId());
        formFields.put("amount", amount);
        formFields.put("productinfo", productInfo);
        formFields.put("firstname", firstName);
        formFields.put("email", email);
        formFields.put("phone", request.customerPhone());
        formFields.put("surl", payUConfigProperties.successUrl());
        formFields.put("furl", payUConfigProperties.failureUrl());
        formFields.put("hash", hash);

        String actionUrl = payUConfigProperties.baseUrl() + payUConfigProperties.paymentEndpoint();
        return new PaymentInitiationResult(actionUrl, formFields);
    }

    @Override
    public PaymentVerificationResult verifyCallback(Map<String, String> callbackParams) {
        String key = callbackParams.get("key");
        String txnid = callbackParams.get("txnid");
        String amount = callbackParams.get("amount");
        String productinfo = callbackParams.get("productinfo");
        String firstname = callbackParams.get("firstname");
        String email = callbackParams.get("email");
        String status = callbackParams.get("status");
        String receivedHash = callbackParams.get("hash");
        String mihpayid = callbackParams.get("mihpayid");

        if (key == null || txnid == null || amount == null || status == null || receivedHash == null) {
            throw new PaymentGatewayException("PayU callback is missing required fields", HttpStatus.BAD_GATEWAY.value());
        }

        if (!payUConfigProperties.merchantKey().equals(key)) {
            throw new PaymentGatewayException("PayU callback key does not match configured merchant key", HttpStatus.BAD_GATEWAY.value());
        }

        String expectedHash = PayUHashUtil.responseHash(
                payUConfigProperties.merchantSalt(),
                status,
                callbackParams.get("udf1"), callbackParams.get("udf2"), callbackParams.get("udf3"),
                callbackParams.get("udf4"), callbackParams.get("udf5"),
                email, firstname, productinfo, amount, txnid, key
        );

        if (!constantTimeEquals(expectedHash, receivedHash)) {
            LOGGER.warn("PayU callback hash mismatch for txnid {} - rejecting as untrusted", txnid);
            throw new PaymentGatewayException("PayU callback hash verification failed", HttpStatus.BAD_GATEWAY.value());
        }

        boolean success = STATUS_SUCCESS.equalsIgnoreCase(status);
        return new PaymentVerificationResult(success, txnid, mihpayid, new BigDecimal(amount), status);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
