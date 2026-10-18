package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.error.ErrorCode;
import com.shiporbit.backend.exception.PartnerApiException;
import com.shiporbit.backend.rate.auth.CachingTokenProvider;
import com.shiporbit.backend.rate.auth.DelhiveryTokenFetcher;
import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.dto.response.RateResponse;
import com.shiporbit.backend.rate.routing.DelhiveryConfigProperties;
import com.shiporbit.backend.rate.serviceability.Serviceability;
import com.shiporbit.backend.rate.serviceability.ServiceabilityCache;
import com.shiporbit.backend.rate.serviceability.ServiceabilityJson;
import com.shiporbit.backend.rate.util.CurlLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DelhiveryPartnerClient implements DeliveryPartnerClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(DelhiveryPartnerClient.class);

    private final RestClient delhiveryClient;
    private final DelhiveryConfigProperties delhiveryConfigProperties;
    private final CachingTokenProvider tokenProvider;
    private final DelhiveryB2cRateClient b2cRateClient;
    private final ServiceabilityCache serviceabilityCache;

    public DelhiveryPartnerClient(RestClient delhiveryClient,
                                  DelhiveryConfigProperties delhiveryConfigProperties,
                                  DelhiveryTokenFetcher delhiveryTokenFetcher,
                                  DelhiveryB2cRateClient b2cRateClient,
                                  ServiceabilityCache serviceabilityCache) {
        this.delhiveryClient = delhiveryClient;
        this.delhiveryConfigProperties = delhiveryConfigProperties;
        this.b2cRateClient = b2cRateClient;
        this.serviceabilityCache = serviceabilityCache;
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
        return !checkServiceability(request).notServiceable();
    }

    /**
     * Same weight split as getRate(): below 20 kg -> B2C pin-codes API, otherwise -> LTL pincode-service.
     * Both origin and destination are checked; each pincode answer is cached (ServiceabilityCache).
     */
    @Override
    public Serviceability checkServiceability(RequestParamRecord request) {
        if (b2cRateClient.handles(request)) {
            return b2cRateClient.checkLane(request);
        }
        long weightKg = (long) Math.ceil((request.weight() == null ? 0 : request.weight()) / 1000.0);
        Serviceability origin = ltlPincode(request.sourcePinCode(), weightKg);
        Serviceability destination = ltlPincode(request.destinationPinCode(), weightKg);
        return Serviceability.both(origin, destination);
    }

    // LTL: GET {router-url}/pincode-service/{pin}?weight=<w>, Bearer = LTL login JWT.
    // Unconfirmed: weight unit (sent as kg - the sample used 200, which only makes sense as kg for B2B)
    // and response shape (parsed by searching for a serviceable/isServiceable flag; not found = UNKNOWN).
    private Serviceability ltlPincode(String pin, long weightKg) {
        return serviceabilityCache.get("delhivery:ltl:pin:" + pin + ":w" + weightKg, () -> {
            String path = delhiveryConfigProperties.pincodeEndpoint() + "/" + pin + "?weight=" + weightKg;
            Object response;
            try {
                String token = tokenProvider.getValidToken().get("token");
                response = delhiveryClient.get().uri(path)
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token)
                        .retrieve().body(Object.class);
            } catch (HttpStatusCodeException e) {
                LOGGER.warn("Delhivery LTL pincode-service {} for {}: {}", e.getStatusCode(), pin, e.getResponseBodyAsString());
                return Serviceability.unknown(Serviceability.codeForHttp(e.getStatusCode().value()), "Delhivery LTL pincode-service HTTP " + e.getStatusCode().value());
            } catch (RuntimeException e) {
                LOGGER.warn("Delhivery LTL pincode-service failed for {}: {}", pin, e.getMessage());
                return Serviceability.unknown(e instanceof PartnerApiException pae ? pae.errorCode() : PartnerApiException.codeForCause(e), "Delhivery LTL pincode-service failed: " + e.getMessage());
            }
            LOGGER.debug("Delhivery LTL pincode-service response for {} (weight {} kg): {}", pin, weightKg, response);
            return ServiceabilityJson.findFlag(response)
                    .map(ok -> ok ? Serviceability.yes("LTL pin " + pin + " serviceable")
                                  : Serviceability.no(ErrorCode.NOT_SERVICEABLE, "Delhivery LTL does not serve pincode " + pin))
                    .orElseGet(() -> Serviceability.unknown(ErrorCode.SERVICEABILITY_UNCONFIRMED, "Delhivery LTL pincode-service: no serviceable flag in response"));
        });
    }

    /**
     * Routes by dead weight: below rate-aggregator.delhivery-b2c.max-weight-kg (default 20 kg) -> B2C
     * express-parcel API; otherwise -> LTL freight API (unchanged flow below).
     */
    @Override
    public RateResponse getRate(RequestParamRecord request) {
        if (b2cRateClient.handles(request)) {
            LOGGER.debug("Fetching Delhivery B2C rate: {} -> {}, weight={}g", request.sourcePinCode(), request.destinationPinCode(), request.weight());
            return b2cRateClient.getRate(request, partnerCode(), partnerName());
        }
        LOGGER.debug("Fetching Delhivery LTL rate: {} -> {}, weight={}g", request.sourcePinCode(), request.destinationPinCode(), request.weight());
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
        // Raw "data" block (no credentials in it): lets us check mapToRateResponse() against real numbers.
        LOGGER.debug("Delhivery rate-calculator response data: {}", response == null ? null : response.get("data"));
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

        Map<String, Object> body = new LinkedHashMap<>(Map.ofEntries(
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
        ));

        // COD: Delhivery rejects payment_mode=cod without cod_amount
        // (real error 2026-10-11: "payment_mode: Missing required key: 'cod_amount'").
        // partnerOptions key "codAmount" (Number); falls back to the invoice amount when absent.
        // Sent as a string like weight_g / inv_amount - unconfirmed; change if Delhivery rejects the type.
        if ("cod".equalsIgnoreCase(request.paymentMode())) {
            Object codAmount = request.partnerOptions().getOrDefault("codAmount", request.inventoryAmout());
            body.put("cod_amount", String.valueOf(codAmount));
        }
        return body;
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

        BigDecimal odaCharge = bigDecimalOf(oda, "fm").add(bigDecimalOf(oda, "lm"));

        // Everything that isn't base freight / fuel / insurance / GST gets bucketed
        // into handlingCharges, since RateResponse doesn't have a slot per sub-charge.
        // meta_charges is NOT added: it is the itemised detail OF other_handling_charges.
        // Real response (2026-10-11): other_handling_charges=200, meta_charges.to_pay=200, and
        // base 469 + fuel_surcharge 525 + fuel_hike 80 + insurance_rov 200 + oda.fm 100
        // + other_handling 200 = 1574 = pre_tax_freight_charges. Adding meta_charges counted 200 twice.
        // Top-level fm/lm (150/0 in that response) are also not part of pre_tax_freight_charges.
        BigDecimal handlingCharges = bigDecimalOf(priceBreakup, "other_handling_charges")
                .add(bigDecimalOf(priceBreakup, "appointment_charges"))
                .add(bigDecimalOf(priceBreakup, "green"))
                .add(bigDecimalOf(priceBreakup, "markup"));

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

    private BigDecimal bigDecimalOf(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.toString());
    }
}
