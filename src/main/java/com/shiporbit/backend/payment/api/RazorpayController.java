package com.shiporbit.backend.payment.api;

import com.razorpay.RazorpayClient;
import com.shiporbit.backend.payment.dto.PaymentInitiationRequest;
import com.shiporbit.backend.payment.service.PaymentGatewayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payment")
public class RazorpayController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RazorpayController.class);

    PaymentGatewayClient paymentGatewayClient;

    @Autowired
    public RazorpayController(PaymentGatewayClient paymentGatewayClient) {
        this.paymentGatewayClient = paymentGatewayClient;
    }

    @PostMapping("/create-order")
    public Map<String,String> createOrder(@RequestBody PaymentInitiationRequest requestWallet){
        LOGGER.info("Initiated the payment recharge request : {} by the user {} ",requestWallet.amount(),requestWallet.customerEmail());

//        return paymentGatewayClient.initiate(requestWallet);
        return null;
    }
}
