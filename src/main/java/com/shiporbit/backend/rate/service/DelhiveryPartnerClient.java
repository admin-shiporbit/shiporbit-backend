package com.shiporbit.backend.rate.service;

// Imports: Spring RestClient, config, token providers, B2C client, serviceability cache, error types.
import com.shiporbit.backend.error.ErrorCode;
import com.shiporbit.backend.exception.PartnerApiException;
import com.shiporbit.backend.rate.auth.CachingTokenProvider;
import com.shiporbit.backend.rate.auth.DelhiveryTokenFetcher;
import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.dto.response.RateResponse;
import com.shiporbit.backend.rate.routing.DelhiveryConfigProperties;
import com.shiporbit.backend.rate.serviceability.Serviceability;
import com.shiporbit.backend.rate.serviceability.ServiceabilityCache;
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

// Spring bean; picked up by LivePartnerQuoteProvider as partner 1.
@Component
// Delhivery live client (B2C below 20 kg, LTL from 20 kg).
public class DelhiveryPartnerClient implements DeliveryPartnerClient {

    // Logger.
    private static final Logger LOGGER = LoggerFactory.getLogger(DelhiveryPartnerClient.class);

    // RestClient with Delhivery LTL base URL (DelhiveryClientConfig).
    private final RestClient delhiveryClient;
    // LTL endpoints + credentials config.
    private final DelhiveryConfigProperties delhiveryConfigProperties;
    // LTL login JWT, cached until expiry.
    private final CachingTokenProvider tokenProvider;
    // B2C (express parcel) client.
    private final DelhiveryB2cRateClient b2cRateClient;
    // 12 h cache of pincode answers.
    private final ServiceabilityCache serviceabilityCache;

    // Constructor injection:
    public DelhiveryPartnerClient(RestClient delhiveryClient,
                                  DelhiveryConfigProperties delhiveryConfigProperties,
                                  DelhiveryTokenFetcher delhiveryTokenFetcher,
                                  DelhiveryB2cRateClient b2cRateClient,
                                  ServiceabilityCache serviceabilityCache) {
        // store RestClient,
        this.delhiveryClient = delhiveryClient;
        // store config,
        this.delhiveryConfigProperties = delhiveryConfigProperties;
        // store B2C client,
        this.b2cRateClient = b2cRateClient;
        // store cache,
        this.serviceabilityCache = serviceabilityCache;
        // CachingTokenProvider is a plain object (not a @Component) on purpose: each
        // partner gets its own instance wrapping its own TokenFetcher. Built here so
        // Delhivery's token cache is scoped to this client.
        // own token cache wrapping the LTL login (DelhiveryTokenFetcher).
        this.tokenProvider = new CachingTokenProvider(delhiveryTokenFetcher);
    }

    @Override
    // Code used by LivePartnerQuoteProvider to map to partner id 1.
    public String partnerCode() {
        // "delhivery".
        return "delhivery";
    }

    @Override
    // Display name fallback.
    public String partnerName() {
        // "Delhivery".
        return "Delhivery";
    }

    @Override
    // Boolean form (used by other endpoints).
    public boolean isServiceable(RequestParamRecord request) {
        // Serviceable unless NOT_SERVICEABLE.
        return !checkServiceability(request).notServiceable();
    }

    /**
     * Same weight split as getRate(): below 20 kg -> B2C pin-codes API, otherwise -> LTL pincode-service.
     * Both origin and destination are checked; each pincode answer is cached (ServiceabilityCache).
     */
    @Override
    // Step 3d - serviceability for Delhivery.
    public Serviceability checkServiceability(RequestParamRecord request) {
        // Weight < B2C limit (default 20 kg)?
        if (b2cRateClient.handles(request)) {
            // Log step.
            com.shiporbit.backend.logging.Flow.step(LOGGER, "Delhivery: B2C pin-codes API for origin and destination");
            // B2C pin-codes API for origin and destination (DelhiveryB2cRateClient.checkLane).
            return b2cRateClient.checkLane(request);
        }
        // Otherwise LTL. Log step.
        com.shiporbit.backend.logging.Flow.step(LOGGER, "Delhivery: LTL pincode-service for origin and destination");
        // Grams -> kg, rounded up.
        long weightKg = (long) Math.ceil((request.weight() == null ? 0 : request.weight()) / 1000.0);
        // COD shipment?
        boolean cod = "cod".equalsIgnoreCase(request.paymentMode());
        // Check origin (cached pin answer), then require pickup there.
        Serviceability origin = ltlEvaluate(ltlPincode(request.sourcePinCode(), weightKg), request.sourcePinCode(), true, cod);
        // Check destination (cached pin answer), then require COD or prepaid there.
        Serviceability destination = ltlEvaluate(ltlPincode(request.destinationPinCode(), weightKg), request.destinationPinCode(), false, cod);
        // SERVICEABLE only if both are; NOT_SERVICEABLE if either is; else UNKNOWN.
        return Serviceability.both(origin, destination);
    }

    // LTL: GET {router-url}/pincode-service/{pin}?weight=<w>, Bearer = LTL login JWT.
    // Unconfirmed: weight unit (sent as kg - the sample used 200, which only makes sense as kg for B2B).
    // Response shape confirmed 2026-11-09 (see DelhiveryLtlPinParser): 200 = pincode_serviceability_data,
    // 400 "pin: X is not serviceable." = definite NO.
    // One LTL pincode check.
    private Serviceability ltlPincode(String pin, long weightKg) {
        // Cache key per pincode + weight; the lambda runs only on a cache miss (result kept 12 h).
        return serviceabilityCache.get("delhivery:ltl:pin:" + pin + ":w" + weightKg, () -> {
            // GET path: pincode-service/{pin}?weight={kg}.
            String path = delhiveryConfigProperties.pincodeEndpoint() + "/" + pin + "?weight=" + weightKg;
            // Raw answer.
            Object response;
            // Try:
            try {
                // LTL bearer token (login on first use / after expiry).
                String token = tokenProvider.getValidToken().get("token");
                // GET request
                response = delhiveryClient.get().uri(path)
                        // JSON content type,
                        .header("Content-Type", "application/json")
                        // Bearer token,
                        .header("Authorization", "Bearer " + token)
                        // read body as generic JSON.
                        .retrieve().body(Object.class);
            // HTTP 4xx / 5xx:
            } catch (HttpStatusCodeException e) {
                // log warning,
                LOGGER.warn("Delhivery LTL pincode-service {} for {}: {}", e.getStatusCode(), pin, e.getResponseBodyAsString());
                // Delhivery says "not serviceable" as HTTP 400 -> NOT_SERVICEABLE (cached).
                Serviceability no = DelhiveryLtlPinParser.fromHttpError(e.getStatusCode().value(), e.getResponseBodyAsString(), pin);
                if (no != null) {
                    return no;
                }
                // Any other HTTP error -> UNKNOWN with an error code for the status (rate will NOT be fetched).
                return Serviceability.unknown(Serviceability.codeForHttp(e.getStatusCode().value()), "Delhivery LTL pincode-service HTTP " + e.getStatusCode().value());
            // Connection / parsing problem:
            } catch (RuntimeException e) {
                // log warning,
                LOGGER.warn("Delhivery LTL pincode-service failed for {}: {}", pin, e.getMessage());
                // -> UNKNOWN with a matching error code.
                return Serviceability.unknown(e instanceof PartnerApiException pae ? pae.errorCode() : PartnerApiException.codeForCause(e), "Delhivery LTL pincode-service failed: " + e.getMessage());
            }
            // Debug log of the answer.
            LOGGER.debug("Delhivery LTL pincode-service response for {} (weight {} kg): {}", pin, weightKg, response);
            // pincode_serviceability_data row for this pin -> SERVICEABLE with "pickup=Y,cod=Y,pre_paid=Y,is_oda=N"; else UNKNOWN.
            return DelhiveryLtlPinParser.parse(response, pin);
        });
    }

    // Origin needs pickup; destination needs COD (COD shipment) or prepaid. Not-SERVICEABLE answers pass through.
    private static Serviceability ltlEvaluate(Serviceability s, String pin, boolean origin, boolean cod) {
        // NOT_SERVICEABLE / UNKNOWN: return as is.
        if (s.status() != Serviceability.Status.SERVICEABLE) {
            return s;
        }
        // Origin: pickup flag.
        if (origin) {
            return DelhiveryPinParser.flag(s.reason(), "pickup")
                    ? Serviceability.yes("LTL origin " + pin + " pickup OK")
                    : Serviceability.no(ErrorCode.PICKUP_NOT_AVAILABLE, "Delhivery LTL: no pickup at origin " + pin);
        }
        // Destination: COD or prepaid flag.
        String key = cod ? "cod" : "pre_paid";
        return DelhiveryPinParser.flag(s.reason(), key)
                ? Serviceability.yes("LTL destination " + pin + " " + key + " OK")
                : Serviceability.no(cod ? ErrorCode.COD_NOT_SERVICEABLE : ErrorCode.PREPAID_NOT_SERVICEABLE,
                        "Delhivery LTL: " + (cod ? "COD" : "prepaid") + " not available at destination " + pin);
    }

    /**
     * Routes by dead weight: below rate-aggregator.delhivery-b2c.max-weight-kg (default 20 kg) -> B2C
     * express-parcel API; otherwise -> LTL freight API (unchanged flow below).
     */
    @Override
    // Step 3d - rate for Delhivery (called only after SERVICEABLE).
    public RateResponse getRate(RequestParamRecord request) {
        // Weight < B2C limit?
        if (b2cRateClient.handles(request)) {
            // Log step.
            com.shiporbit.backend.logging.Flow.step(LOGGER, "Delhivery: B2C rate API ({} g < B2C limit) {} -> {}",
                    request.weight(), request.sourcePinCode(), request.destinationPinCode());
            // B2C invoice-charges API (DelhiveryB2cRateClient.getRate).
            return b2cRateClient.getRate(request, partnerCode(), partnerName());
        }
        // Otherwise LTL. Log step.
        com.shiporbit.backend.logging.Flow.step(LOGGER, "Delhivery: LTL rate API ({} g >= B2C limit) {} -> {}",
                request.weight(), request.sourcePinCode(), request.destinationPinCode());
        // LTL bearer token.
        String token = tokenProvider.getValidToken().get("token");

        // LTL request body.
        Map<String, Object> requestBody = buildRequestBody(request);
        // Full URL (for the debug curl only).
        String url = delhiveryConfigProperties.routerUrl() + delhiveryConfigProperties.ratecalculatorEndpoint();
        // Debug: request as a curl (token masked by the logging masker).
        LOGGER.debug("Outgoing Delhivery rate-calculator request:\n{}", CurlLogger.toCurl("POST", url,
                Map.of("Content-Type", "application/json", "Accept", "application/json", "Authorization", "Bearer " + token),
                requestBody));
        // Raw answer.
        Map response;
        // Try:
        try {
            // POST
            response = delhiveryClient.post()
                    // rate-calculator endpoint,
                    .uri(delhiveryConfigProperties.ratecalculatorEndpoint())
                    // JSON body,
                    .header("Content-Type", "application/json")
                    // JSON answer,
                    .header("Accept", "application/json")
                    // Bearer token,
                    .header("Authorization", "Bearer " + token)
                    // body,
                    .body(requestBody)
                    // send,
                    .retrieve()
                    // read as Map.
                    .body(Map.class);
        // HTTP 4xx / 5xx:
        } catch (HttpStatusCodeException e) {
            // -> PartnerApiException (caught in LivePartnerQuoteProvider.await -> Unavailable).
            throw PartnerApiException.from(e, "Delhivery");
        // Connection problem:
        } catch (RestClientException e) {
            // -> PartnerApiException 502.
            throw new PartnerApiException("Unable to connect to Delhivery freight-estimate API", e, HttpStatus.BAD_GATEWAY.value());
        }
        // Raw "data" block (no credentials in it): lets us check mapToRateResponse() against real numbers.
        // Debug: the "data" block.
        LOGGER.debug("Delhivery rate-calculator response data: {}", response == null ? null : response.get("data"));
        // Map to RateResponse.
        RateResponse rateResponse = mapToRateResponse(response);
        // Debug: final freight.
        LOGGER.debug("Delhivery rate fetched: finalFreight={}", rateResponse.finalFreight());
        // Return to LivePartnerQuoteProvider.quoteOne.
        return rateResponse;
    }

    // Confirmed final contract (2026-10-01): weight_g and inv_amount are sent as
    // strings (not numbers), and two fields beyond the common RequestParamRecord ones
    // are required - freight_mode (request.freightMode(), e.g. "fod") and pt, which
    // duplicates payment_mode's value (Delhivery's own API asks for it as a separate
    // field - not something we can collapse away).
    // LTL rate request body.
    private Map<String, Object> buildRequestBody(RequestParamRecord request) {
        // Box dimensions.
        var dimension = request.dimension();
        // One dimensions entry:
        Map<String, Object> dimensionEntry = Map.of(
                // length,
                "length_cm", dimension.getLength(),
                // width,
                "width_cm", dimension.getWidth(),
                // height,
                "height_cm", dimension.getHeight(),
                // box count.
                "box_count", dimension.getBoxCount()
        );

        // Body in insertion order:
        Map<String, Object> body = new LinkedHashMap<>(Map.ofEntries(
                // origin pincode,
                Map.entry("source_pin", request.sourcePinCode()),
                // destination pincode,
                Map.entry("consignee_pin", request.destinationPinCode()),
                // cheque payment,
                Map.entry("cheque_payment", request.chequePayment()),
                // ROV insurance,
                Map.entry("rov_insurance", request.isRovInsurance()),
                // weight in grams (string),
                Map.entry("weight_g", String.valueOf(request.weight())),
                // payment mode (prepaid / cod),
                Map.entry("payment_mode", request.paymentMode()),
                // invoice amount (string),
                Map.entry("inv_amount", String.valueOf(request.inventoryAmout())),
                // freight mode (fod),
                Map.entry("freight_mode", request.freightMode()),
                // pt = payment mode again (required by Delhivery),
                Map.entry("pt", request.paymentMode()),
                // dimensions list.
                Map.entry("dimensions", List.of(dimensionEntry))
        ));

        // COD: Delhivery rejects payment_mode=cod without cod_amount
        // (real error 2026-10-11: "payment_mode: Missing required key: 'cod_amount'").
        // partnerOptions key "codAmount" (Number); falls back to the invoice amount when absent.
        // Sent as a string like weight_g / inv_amount - unconfirmed; change if Delhivery rejects the type.
        // COD?
        if ("cod".equalsIgnoreCase(request.paymentMode())) {
            // COD amount from options (fallback invoice amount),
            Object codAmount = request.partnerOptions().getOrDefault("codAmount", request.inventoryAmout());
            // sent as cod_amount (string).
            body.put("cod_amount", String.valueOf(codAmount));
        }
        // Body ready.
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
    // LTL answer -> RateResponse.
    private RateResponse mapToRateResponse(Map<String, Object> response) {
        // "data" block (empty map if missing).
        Map<String, Object> data = asMap(response.get("data"));
        // "price_breakup".
        Map<String, Object> priceBreakup = asMap(data.get("price_breakup"));
        // "oda" (fm / lm).
        Map<String, Object> oda = asMap(priceBreakup.get("oda"));

        // ODA = first mile + last mile.
        BigDecimal odaCharge = bigDecimalOf(oda, "fm").add(bigDecimalOf(oda, "lm"));

        // Everything that isn't base freight / fuel / insurance / GST gets bucketed
        // into handlingCharges, since RateResponse doesn't have a slot per sub-charge.
        // meta_charges is NOT added: it is the itemised detail OF other_handling_charges.
        // Real response (2026-10-11): other_handling_charges=200, meta_charges.to_pay=200, and
        // base 469 + fuel_surcharge 525 + fuel_hike 80 + insurance_rov 200 + oda.fm 100
        // + other_handling 200 = 1574 = pre_tax_freight_charges. Adding meta_charges counted 200 twice.
        // Top-level fm/lm (150/0 in that response) are also not part of pre_tax_freight_charges.
        // Handling = other_handling_charges
        BigDecimal handlingCharges = bigDecimalOf(priceBreakup, "other_handling_charges")
                // + appointment charges
                .add(bigDecimalOf(priceBreakup, "appointment_charges"))
                // + green tax
                .add(bigDecimalOf(priceBreakup, "green"))
                // + markup.
                .add(bigDecimalOf(priceBreakup, "markup"));

        // RateResponse:
        return new RateResponse(
                // partner code,
                partnerCode(),
                // name,
                partnerName(),
                // base freight,
                bigDecimalOf(priceBreakup, "base_freight_charge"),
                // fuel hike,
                bigDecimalOf(priceBreakup, "fuel_hike"),
                // fuel surcharge,
                bigDecimalOf(priceBreakup, "fuel_surcharge"),
                // ROV insurance,
                bigDecimalOf(priceBreakup, "insurance_rov"),
                // ODA,
                odaCharge,
                // handling,
                handlingCharges,
                // partner GST,
                bigDecimalOf(priceBreakup, "gst"),
                // partner total (incl. GST).
                bigDecimalOf(data, "total")
        );
    }

    // Safe cast to Map.
    private Map<String, Object> asMap(Object value) {
        // Map -> itself; anything else -> empty map.
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    // Number at key, as BigDecimal.
    private BigDecimal bigDecimalOf(Map<String, Object> map, String key) {
        // Value.
        Object value = map.get(key);
        // Missing?
        if (value == null) {
            // -> 0.
            return BigDecimal.ZERO;
        }
        // Parse from text.
        return new BigDecimal(value.toString());
    }
}
