package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.exception.DelhiveryApiException;
import com.shiporbit.backend.rate.auth.CachingTokenProvider;
import com.shiporbit.backend.rate.auth.DelhiveryTokenFetcher;
import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.dto.response.RateResponse;
import com.shiporbit.backend.rate.routing.DelhiveryConfigProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class DelhiveryPartnerClient implements DeliveryPartnerClient {

    private final RestClient delhiveryClient;
    private final DelhiveryConfigProperties delhiveryConfigProperties;
    private final CachingTokenProvider tokenProvider;

    public DelhiveryPartnerClient(RestClient delhiveryClient,
                                  DelhiveryConfigProperties delhiveryConfigProperties,
                                  DelhiveryTokenFetcher delhiveryTokenFetcher) {
        this.delhiveryClient = delhiveryClient;
        this.delhiveryConfigProperties = delhiveryConfigProperties;
        // CachingTokenProvider is a plain object (not a @Component) on purpose: each
        // partner gets its own instance wrapping its own TokenFetcher. Built here so
        // Delhivery's token cache is scoped to this client.
        this.tokenProvider = new CachingTokenProvider(delhiveryTokenFetcher);
    }

    @Override
    public String partnerCode() {
        return "delhivery";
    }

    @Override
    public String partnerName() {
        return "Delhivery";
    }

    @Override
    public boolean isServiceable(RequestParamRecord request) {
        // TODO: replace with a real call to the pincode-service endpoint
        // (delhiveryConfigProperties.pincodeEndpoint()) once its request/response
        // contract has been confirmed via curl.
        return true;
    }

    @Override
    public RateResponse getRate(RequestParamRecord request) {
        String token = tokenProvider.getValidToken();

        Map<String, Object> requestBody = buildRequestBody(request);
        Map response;
        try {
            response = delhiveryClient.post()
                    .uri(delhiveryConfigProperties.ratecalculatorEndpoint())
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);
        } catch (HttpStatusCodeException e) {
            throw DelhiveryApiException.from(e);
        } catch (RestClientException e) {
            throw new DelhiveryApiException("Unable to connect to Delhivery freight-estimate API", e, HttpStatus.BAD_GATEWAY.value());
        }
        return mapToRateResponse(response);
    }

    private Map<String, Object> buildRequestBody(RequestParamRecord request) {
        var dimension = request.dimension();
        Map<String, Object> dimensionEntry = Map.of(
                "length_cm", dimension.getLength(),
                "width_cm", dimension.getWidth(),
                "height_cm", dimension.getHeight(),
                "box_count", dimension.getBoxCount()
        );

        return Map.of(
                "source_pin", request.sourcePinCode(),
                "consignee_pin", request.destinationPinCode(),
                "cheque_payment", request.chequePayment(),
                "rov_insurance", request.isRovInsurance(),
                "weight_g", request.weight(),
                "payment_mode", request.paymentMode(),
                "inv_amount", request.inventoryAmout(),
                "dimensions", List.of(dimensionEntry)
        );
    }

    // Real response shape (confirmed via curl):
    // {success, data: {total, charged_wt, min_charged_wt, price_breakup: {
    //   base_freight_charge, fuel_surcharge, fuel_hike, insurance_rov,
    //   oda: {fm, lm}, fm, lm, green, pre_tax_freight_charges, markup,
    //   gst, gst_percent, divisor, other_handling_charges,
    //   meta_charges: {cod, demurrage, reattempt, handling, pod, sunday,
    //     to_pay, cheque, csd, add_cost, adh_vhl, sp_dlv_area, add_machine,
    //     add_man_pwr, mathadi_un, floor_delivery, mall_delivery},
    //   appointment_charges
    // }}, request_id}
    private RateResponse mapToRateResponse(Map<String, Object> response) {
        Map<String, Object> data = asMap(response.get("data"));
        Map<String, Object> priceBreakup = asMap(data.get("price_breakup"));
        Map<String, Object> oda = asMap(priceBreakup.get("oda"));
        Map<String, Object> metaCharges = asMap(priceBreakup.get("meta_charges"));

        BigDecimal odaCharge = bigDecimalOf(oda, "fm").add(bigDecimalOf(oda, "lm"));

        // Everything that isn't base freight / fuel / insurance / GST gets bucketed
        // into handlingCharges, since RateResponse doesn't have a slot per sub-charge.
        BigDecimal handlingCharges = bigDecimalOf(priceBreakup, "other_handling_charges")
                .add(bigDecimalOf(priceBreakup, "appointment_charges"))
                .add(bigDecimalOf(priceBreakup, "green"))
                .add(bigDecimalOf(priceBreakup, "markup"))
                .add(sumOf(metaCharges));

        return new RateResponse(
                partnerCode(),
                partnerName(),
                bigDecimalOf(priceBreakup, "base_freight_charge"),
                bigDecimalOf(priceBreakup, "fuel_hike"),
                bigDecimalOf(priceBreakup, "fuel_surcharge"),
                bigDecimalOf(priceBreakup, "insurance_rov"),
                odaCharge,
                handlingCharges,
                bigDecimalOf(priceBreakup, "gst"),
                bigDecimalOf(data, "total")
        );
    }

    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private BigDecimal sumOf(Map<String, Object> values) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Object value : values.values()) {
            if (value != null) {
                sum = sum.add(new BigDecimal(value.toString()));
            }
        }
        return sum;
    }

    private BigDecimal bigDecimalOf(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.toString());
    }
}
