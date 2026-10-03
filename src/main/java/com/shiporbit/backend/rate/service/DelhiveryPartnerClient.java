package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.exception.PartnerApiException;
import com.shiporbit.backend.rate.auth.CachingTokenProvider;
import com.shiporbit.backend.rate.auth.DelhiveryTokenFetcher;
import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.dto.response.RateResponse;
import com.shiporbit.backend.rate.routing.DelhiveryConfigProperties;
import com.shiporbit.backend.rate.util.CurlLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(DelhiveryPartnerClient.class);

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
        LOGGER.debug("Fetching Delhivery rate: {} -> {}, weight={}g", request.sourcePinCode(), request.destinationPinCode(), request.weight());
        String token = tokenProvider.getValidToken().get("token");

        Map<String, Object> requestBody = buildRequestBody(request);
        String url = delhiveryConfigProperties.routerUrl() + delhiveryConfigProperties.ratecalculatorEndpoint();
        LOGGER.debug("Outgoing Delhivery rate-calculator request:\n{}", CurlLogger.toCurl("POST", url,
                Map.of("Content-Type", "application/json", "Accept", "application/json", "Authorization", "Bearer " + token),
                requestBody));
        Map response;
        try {
            response = delhiveryClient.post()
                    .uri(delhiveryConfigProperties.ratecalculatorEndpoint())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);
        } catch (HttpStatusCodeException e) {
            throw PartnerApiException.from(e, "Delhivery");
        } catch (RestClientException e) {
            throw new PartnerApiException("Unable to connect to Delhivery freight-estimate API", e, HttpStatus.BAD_GATEWAY.value());
        }
        RateResponse rateResponse = mapToRateResponse(response);
        LOGGER.debug("Delhivery rate fetched: finalFreight={}", rateResponse.finalFreight());
        return rateResponse;
    }

    // Confirmed final contract (2026-10-01): weight_g and inv_amount are sent as
    // strings (not numbers), and two fields beyond the common RequestParamRecord ones
    // are required - freight_mode (request.freightMode(), e.g. "fod") and pt, which
    // duplicates payment_mode's value (Delhivery's own API asks for it as a separate
    // field - not something we can collapse away).
    private Map<String, Object> buildRequestBody(RequestParamRecord request) {
        var dimension = request.dimension();
        Map<String, Object> dimensionEntry = Map.of(
                "length_cm", dimension.getLength(),
                "width_cm", dimension.getWidth(),
                "height_cm", dimension.getHeight(),
                "box_count", dimension.getBoxCount()
        );

        return Map.ofEntries(
                Map.entry("source_pin", request.sourcePinCode()),
                Map.entry("consignee_pin", request.destinationPinCode()),
                Map.entry("cheque_payment", request.chequePayment()),
                Map.entry("rov_insurance", request.isRovInsurance()),
                Map.entry("weight_g", String.valueOf(request.weight())),
                Map.entry("payment_mode", request.paymentMode()),
                Map.entry("inv_amount", String.valueOf(request.inventoryAmout())),
                Map.entry("freight_mode", request.freightMode()),
                Map.entry("pt", request.paymentMode()),
                Map.entry("dimensions", List.of(dimensionEntry))
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
