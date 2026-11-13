package com.shiporbit.backend.rate.service;

// Imports: RestClient, Jackson, config, token provider, serviceability cache, error types, curl logger.
import com.shiporbit.backend.error.ErrorCode;
import com.shiporbit.backend.exception.PartnerApiException;
import com.shiporbit.backend.rate.auth.CachingTokenProvider;
import com.shiporbit.backend.rate.auth.ShreeMarutiTokenFetcher;
import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.dto.response.RateResponse;
import com.shiporbit.backend.rate.core.HyperlocalAddress;
import com.shiporbit.backend.rate.routing.ShreeMarutiConfigProperties;
import com.shiporbit.backend.rate.serviceability.Serviceability;
import com.shiporbit.backend.rate.serviceability.ServiceabilityCache;
import com.shiporbit.backend.rate.serviceability.ServiceabilityJson;
import com.shiporbit.backend.rate.util.CurlLogger;
import tools.jackson.databind.ObjectMapper;
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

// Spring bean; picked up by LivePartnerQuoteProvider as partner 13 (SHREE_MARUTI).
@Component
// Shree Maruti live client.
public class ShreeMarutiPartnerClient implements DeliveryPartnerClient {

    // Logger.
    private static final Logger LOGGER = LoggerFactory.getLogger(ShreeMarutiPartnerClient.class);
    // Pre-serializing the body to a String (below) instead of handing the raw Map to
    // .body(...) forces Spring to route it through StringHttpMessageConverter instead
    // of the Jackson message converter. The Jackson converter streams JSON straight to
    // the connection's OutputStream without knowing the byte length up front, which
    // makes the JDK-based ClientHttpRequestFactory send it as Transfer-Encoding:
    // chunked. StringHttpMessageConverter knows the length up front and sets
    // Content-Length instead. This matters here specifically because ShreeMaruti's
    // gateway (nginx in front of it, going by the stock "400 Bad Request" HTML page)
    // rejects the chunked request outright - which is exactly why the identical
    // payload succeeds when run as a curl (curl always sends Content-Length) but 400s
    // when sent by this client.
    // Serialises request bodies to a String (forces Content-Length, see note above).
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Named to match the @Bean method name in ShreeMurtiClientConfig (shreeMurtiClient) -
    // there are now two RestClient beans in the context (delhiveryClient, shreeMurtiClient),
    // so Spring needs the parameter name to disambiguate by bean name.
    // RestClient with the Shree Maruti base URL.
    private final RestClient shreeMarutiClient;
    // Endpoints + login config.
    private final ShreeMarutiConfigProperties shreeMarutiConfigProperties;
    // Login token (id_token + tenantId), cached until expiry.
    private final CachingTokenProvider tokenProvider;
    // 12 h serviceability cache.
    private final ServiceabilityCache serviceabilityCache;

    // Constructor injection:
    public ShreeMarutiPartnerClient(RestClient shreeMarutiClient,
                                    ShreeMarutiConfigProperties shreeMarutiConfigProperties,
                                    ShreeMarutiTokenFetcher shreeMarutiTokenFetcher,
                                    ServiceabilityCache serviceabilityCache) {
        // store RestClient,
        this.shreeMarutiClient = shreeMarutiClient;
        // store config,
        this.shreeMarutiConfigProperties = shreeMarutiConfigProperties;
        // store cache,
        this.serviceabilityCache = serviceabilityCache;
        // CachingTokenProvider is a plain object (not a @Component) on purpose: each
        // partner gets its own instance wrapping its own TokenFetcher. Built here so
        // ShreeMurti's token cache is scoped to this client.
        // own token cache wrapping the login (ShreeMurtiTokenFetcher).
        this.tokenProvider = new CachingTokenProvider(shreeMarutiTokenFetcher);
    }

    @Override
    // Code used by LivePartnerQuoteProvider to map to partner id 13.
    public String partnerCode() {
        // "ShreeMaruti" - must equal the key in LivePartnerQuoteProvider.PARTNER_ID_BY_CLIENT_CODE, else the client is ignored.
        return "ShreeMaruti";
    }

    @Override
    // Display name fallback.
    public String partnerName() {
        // "ShreeMurti".
        return "ShreeMurti";
    }

    @Override
    // Boolean form (other endpoints).
    public boolean isServiceable(RequestParamRecord request) {
        // Serviceable unless NOT_SERVICEABLE.
        return !checkServiceability(request).notServiceable();
    }

    // ---- serviceability: same auth as the rate API (Bearer id_token + tenantid), no api-key ----
    // Response shape not yet confirmed: parsed by searching for a serviceable/isServiceable flag.
    // No flag found / HTTP error = UNKNOWN (quote still attempted). Check the DEBUG
    // "ShreeMurti serviceability response" log line and tighten the parser once a real response is seen.

    // Note: UNKNOWN no longer lets the quote continue - LivePartnerQuoteProvider fetches the rate only on SERVICEABLE.
    /** ECOMM lane check: POST /gateway/serviceability/ecomm. Cached per (from, to, payment mode). */
    @Override
    // Step 3d - serviceability for Shree Maruti.
    public Serviceability checkServiceability(RequestParamRecord request) {
        // COD requested (from partner options)?
        boolean cod = Boolean.TRUE.equals(request.partnerOptions().get("cod"));
        // COD / PREPAID.
        String paymentMode = cod ? "COD" : "PREPAID";
        // Cache key per lane + payment mode.
        String key = "shreemurti:ecomm:" + request.sourcePinCode() + ":" + request.destinationPinCode() + ":" + paymentMode;
        // Cached 12 h; lambda runs only on a miss:
        return serviceabilityCache.get(key, () -> {
            // request body,
            Map<String, Object> body = new LinkedHashMap<>();
            // fromPincode (number),
            body.put("fromPincode", Integer.parseInt(request.sourcePinCode()));
            // toPincode (number),
            body.put("toPincode", Integer.parseInt(request.destinationPinCode()));
            // paymentMode,
            body.put("paymentMode", paymentMode);
            // operationType (config),
            body.put("operationType", shreeMarutiConfigProperties.serviceabilityOperationType());
            // carriers (config).
            body.put("carriers", shreeMarutiConfigProperties.serviceabilityCarriers());
            // POST it (callServiceability).
            return callServiceability(shreeMarutiConfigProperties.serviceabilityEcommEndpoint(), body,
                    request.sourcePinCode() + " -> " + request.destinationPinCode());
        });
    }

    /**
     * Hyperlocal check: POST /gateway/serviceability/hyperlocal. Needs address + pincode + lat/long for both ends
     * (QuoteRequest.hyperlocal.pickup / drop). Not used by the quote flow yet - ShreeMurti hyperlocal is not
     * priced (no hyperlocal rate API wired); exposed for POST /api/v1/rates/serviceability.
     */
    // Hyperlocal check - NOT used by /compare (hyperlocal not quoted).
    public Serviceability checkHyperlocal(HyperlocalAddress pickup, HyperlocalAddress drop, boolean cod) {
        // Incomplete addresses?
        if (pickup == null || drop == null || !pickup.complete() || !drop.complete()) {
            // -> UNKNOWN.
            return Serviceability.unknown(ErrorCode.HYPERLOCAL_ADDRESS_REQUIRED, "Hyperlocal check needs address, pincode, latitude, longitude for pickup and drop");
        }
        // COD / PREPAID.
        String paymentMode = cod ? "COD" : "PREPAID";
        // Cache key by coordinates + payment mode.
        String key = "shreemurti:hyperlocal:" + pickup.latitude() + "," + pickup.longitude() + ":"
                + drop.latitude() + "," + drop.longitude() + ":" + paymentMode;
        // Cached call:
        return serviceabilityCache.get(key, () -> {
            // body,
            Map<String, Object> body = new LinkedHashMap<>();
            // pickup address,
            body.put("pickupAddress", address(pickup));
            // drop address,
            body.put("shippingAddress", address(drop));
            // payment mode,
            body.put("paymentMode", paymentMode);
            // operation type,
            body.put("operationType", shreeMarutiConfigProperties.serviceabilityOperationType());
            // carriers.
            body.put("carriers", shreeMarutiConfigProperties.serviceabilityCarriers());
            // POST it.
            return callServiceability(shreeMarutiConfigProperties.serviceabilityHyperlocalEndpoint(), body,
                    pickup.pincode() + " -> " + drop.pincode() + " (hyperlocal)");
        });
    }

    // Address -> map.
    private static Map<String, Object> address(HyperlocalAddress a) {
        // New map:
        Map<String, Object> m = new LinkedHashMap<>();
        // address text,
        m.put("address", a.address());
        // pincode (number),
        m.put("pincode", Integer.parseInt(a.pincode()));
        // latitude,
        m.put("latitude", a.latitude());
        // longitude.
        m.put("longitude", a.longitude());
        // Done.
        return m;
    }

    // POST a serviceability request.
    private Serviceability callServiceability(String endpoint, Map<String, Object> body, String lane) {
        // Headers.
        Map<String, String> headers;
        // Try:
        try {
            // login token (cached),
            Map<String, String> token = tokenProvider.getValidToken();
            // headers:
            headers = Map.of(
                    // JSON,
                    "Content-Type", "application/json",
                    // JSON,
                    "Accept", "application/json",
                    // Bearer id_token,
                    "Authorization", "Bearer " + token.get("id_token"),
                    // tenantid.
                    "tenantid", token.get("tenantId"));
        // Login failed:
        } catch (RuntimeException e) {
            // log warning,
            LOGGER.warn("ShreeMurti login failed for serviceability {}: {}", lane, e.getMessage());
            // -> UNKNOWN (rate NOT fetched).
            return Serviceability.unknown(e instanceof PartnerApiException pae ? pae.errorCode() : ErrorCode.PARTNER_AUTH_FAILED, "ShreeMurti login failed: " + e.getMessage());
        }
        // Debug: request as a curl (token masked).
        LOGGER.debug("Outgoing ShreeMurti serviceability request:\n{}",
                CurlLogger.toCurl("POST", shreeMarutiConfigProperties.routerUrl() + endpoint, headers, body));
        // Raw answer.
        Object response;
        // Try:
        try {
            // POST endpoint,
            response = shreeMarutiClient.post().uri(endpoint)
                    // add headers,
                    .headers(h -> headers.forEach(h::add))
                    // body as String (Content-Length),
                    .body(OBJECT_MAPPER.writeValueAsString(body))   // String body => Content-Length, see OBJECT_MAPPER note
                    // read as generic JSON.
                    .retrieve().body(Object.class);
        // HTTP error:
        } catch (HttpStatusCodeException e) {
            // log warning,
            LOGGER.warn("ShreeMurti serviceability {} for {}: {}", e.getStatusCode(), lane, e.getResponseBodyAsString());
            // -> UNKNOWN with a code for the status.
            return Serviceability.unknown(Serviceability.codeForHttp(e.getStatusCode().value()), "ShreeMurti serviceability HTTP " + e.getStatusCode().value());
        // Unreachable:
        } catch (RestClientException e) {
            // log warning,
            LOGGER.warn("ShreeMurti serviceability unreachable for {}: {}", lane, e.getMessage());
            // -> UNKNOWN.
            return Serviceability.unknown(PartnerApiException.codeForCause(e), "ShreeMurti serviceability API unreachable");
        }
        // Debug log of the answer.
        LOGGER.debug("ShreeMurti serviceability response for {}: {}", lane, response);
        // Search the JSON for a serviceable / isServiceable flag:
        return ServiceabilityJson.findFlag(response)
                // true -> SERVICEABLE,
                .map(ok -> ok ? Serviceability.yes("ShreeMurti serves " + lane)
                              // false -> NOT_SERVICEABLE,
                              : Serviceability.no(ErrorCode.NOT_SERVICEABLE, "ShreeMurti does not serve " + lane))
                // no flag -> UNKNOWN.
                .orElseGet(() -> Serviceability.unknown(ErrorCode.SERVICEABILITY_UNCONFIRMED, "ShreeMurti serviceability: no serviceable flag in response"));
    }

    @Override
    // Step 3d - rate for Shree Maruti (called only after SERVICEABLE).
    public RateResponse getRate(RequestParamRecord request) {
        // Debug log.
        LOGGER.debug("Fetching ShreeMurti rate: {} -> {}, weight={}", request.sourcePinCode(), request.destinationPinCode(), request.weight());
        // Bearer token (cached login).
        String token = tokenProvider.getValidToken().get("id_token");
        // Tenant id (same cached login).
        String tenantId = tokenProvider.getValidToken().get("tenantId");
        // Request body.
        Map<String, Object> requestBody = buildRequestBody(request);
        // Full URL (for the debug curl only).
        String url = shreeMarutiConfigProperties.routerUrl() + shreeMarutiConfigProperties.rateCalculatorEndpoint();
        // Confirmed final contract (2026-10-02): header name is lowercase "tenantid"
        // (not "tenantId"/"tanentId"). "api-key" is NOT required - confirmed by a
        // direct call outside this app with only the bearer token + tenantid. Added
        // "Accept: application/json" to match Delhivery's working header set. Headers
        // are built once into a Map so the debug curl log and the real outgoing
        // request can never drift apart again (they had: the log was edited to drop
        // api-key while the real call still sent it).
        //
        // Even with headers/body matching a manually-run curl exactly (byte for byte,
        // per the debug log output), the app's request still 400'd while the same
        // request via curl succeeded - see the OBJECT_MAPPER / jsonBody comment above
        // for the actual remaining cause (chunked transfer encoding).
        // Headers built once:
        Map<String, String> headers = Map.of(
                // JSON,
                "Content-Type", "application/json",
                // JSON,
                "Accept", "application/json",
                // Bearer token,
                "Authorization", "Bearer " + token,
                // tenantid.
                "tenantid", tenantId);
        // Debug: request as a curl (token masked).
        LOGGER.debug("Outgoing ShreeMurti rate-calculator request:\n{}", CurlLogger.toCurl("POST", url, headers, requestBody));
        // Body as String (Content-Length, not chunked).
        String jsonBody = OBJECT_MAPPER.writeValueAsString(requestBody);
        // Raw answer.
        Map<String, Object> response;
        // Try:
        try {
            // POST
            response = shreeMarutiClient.post()
                    // rate-calculator endpoint,
                    .uri(shreeMarutiConfigProperties.rateCalculatorEndpoint())
                    // headers,
                    .headers(h -> headers.forEach(h::add))
                    // body,
                    .body(jsonBody)
                    // send,
                    .retrieve()
                    // read as Map.
                    .body(Map.class);
        // HTTP error:
        } catch (HttpStatusCodeException e) {
            // -> PartnerApiException (-> Unavailable).
            throw PartnerApiException.from(e, "ShreeMurti");
        // Unreachable:
        } catch (RestClientException e) {
            // -> PartnerApiException 502.
            throw new PartnerApiException("Unable to connect to ShreeMurti rate-calculator API", e, HttpStatus.BAD_GATEWAY.value());
        }
        // Map to RateResponse.
        RateResponse rateResponse = mapToRateResponse(response);
        // Debug: final freight.
        LOGGER.debug("ShreeMurti rate fetched: finalFreight={}", rateResponse.finalFreight());
        // Return to LivePartnerQuoteProvider.quoteOne.
        return rateResponse;
    }

    // partnerOptions keys this client defines (per the extensibility contract - only
    // this class knows about them):
    //   cod              Boolean  - default false
    //   deliveryMode     String   - default "SURFACE"
    //   insuranceAmount  Double   - default 0.0, used only when the common
    //                               isRovInsurance flag is true
    //
    // Unit note: RequestParamRecord.weight() is the app-wide canonical weight, in
    // GRAMS (it's sent as-is into Delhivery's "weight_g" field - see
    // DelhiveryPartnerClient.buildRequestBody()). ShreeMurti's "weight" field expects
    // KILOGRAMS, confirmed by a real request: weight=5000.0 (5000g = 5kg, a sane 5kg
    // parcel for Delhivery) produced a ~220x inflated ShreeMurti quote when sent
    // unconverted - i.e. ShreeMurti priced a 5000kg shipment. Converted below; do not
    // pass request.weight() straight through here again.
    // g -> kg.
    private static final double GRAMS_PER_KG = 1000.0;

    // Rate request body.
    private Map<String, Object> buildRequestBody(RequestParamRecord request) {
        // Partner options from LivePartnerQuoteProvider.
        Map<String, Object> partnerOptions = request.partnerOptions();

        // COD flag (default false).
        boolean cod = (boolean) partnerOptions.getOrDefault("cod", false);
        // Delivery mode (default SURFACE).
        String deliveryMode = (String) partnerOptions.getOrDefault("deliveryMode", "SURFACE");
        // Insurance amount (default 0).
        double insuranceAmount = ((Number) partnerOptions.getOrDefault("insuranceAmount", 0.0)).doubleValue();

        // Insurance block:
        Map<String, Object> insurance = Map.of(
                // enabled = ROV flag (false from compare),
                "enabled", request.isRovInsurance(),
                // amount.
                "amount", insuranceAmount
        );

        // userOptions:
        Map<String, Object> userOptions = Map.of(
                // insurance,
                "insurance", insurance,
                // COD.
                "cod", cod
        );

        // filters:
        Map<String, Object> filters = Map.of(
                // delivery mode.
                "delivery_mode", deliveryMode
        );

        // Grams -> kg (Shree Maruti expects kg).
        double weightKg = request.weight() / GRAMS_PER_KG;

        // Body:
        return Map.ofEntries(
                // fromPincode,
                Map.entry("fromPincode", Integer.parseInt(request.sourcePinCode())),
                // toPincode,
                Map.entry("toPincode", Integer.parseInt(request.destinationPinCode())),
                // serviceType ECOMM,
                Map.entry("serviceType", "ECOMM"),
                // productType ECOMM,
                Map.entry("productType", "ECOMM"),
                // weight (kg),
                Map.entry("weight", weightKg),
                // length,
                Map.entry("length", request.dimension().getLength()),
                // height,
                Map.entry("height", request.dimension().getHeight()),
                // width,
                Map.entry("width", request.dimension().getWidth()),
                // includeDefaultCharges false,
                Map.entry("includeDefaultCharges", false),
                // userOptions,
                Map.entry("userOptions", userOptions),
                // filters.
                Map.entry("filters", filters)
        );
    }

    // Real response shape (sample confirmed 2026-10-01):
    // {status, message, data: {pricing: {baseRate, charges: [{chargeName, chargeCode,
    //   amount, isTax, isTaxable, ...}]}, calculation: {baseAmount, marginAmount,
    //   charges, discounts, taxes, totalAmount}, taxSummary: {..., totalTax}, ...},
    //   trace_id}
    //
    // "charges" is a flat list instead of Delhivery's named sub-fields, so each entry
    // is routed by its chargeCode. taxSummary.totalTax is used as the single GST figure
    // instead of re-summing the tax-flagged charge entries, since it already is that
    // total. Codes not seen in a real response yet (e.g. an insurance or ODA line item)
    // are a guess - confirm against a real payload with insurance/ODA actually applied
    // before relying on insuranceRov/odaCharge here.
    // Rate answer -> RateResponse.
    private RateResponse mapToRateResponse(Map<String, Object> response) {
        // data,
        Map<String, Object> data = asMap(response.get("data"));
        // data.pricing,
        Map<String, Object> pricing = asMap(data.get("pricing"));
        // data.calculation,
        Map<String, Object> calculation = asMap(data.get("calculation"));
        // data.taxSummary,
        Map<String, Object> taxSummary = asMap(data.get("taxSummary"));
        // pricing.charges list.
        List<Map<String, Object>> charges = asListOfMaps(pricing.get("charges"));

        // Fuel surcharge total.
        BigDecimal surcharge = BigDecimal.ZERO;
        // Insurance total.
        BigDecimal insuranceRov = BigDecimal.ZERO;
        // ODA total.
        BigDecimal odaCharge = BigDecimal.ZERO;
        // Everything else.
        BigDecimal handlingCharges = BigDecimal.ZERO;

        // For each charge entry:
        for (Map<String, Object> charge : charges) {
            // tax entry?
            if (Boolean.TRUE.equals(charge.get("isTax"))) {
                // Already captured via taxSummary.totalTax below - skip so it isn't
                // double-counted into one of the charge buckets.
                // skip (GST comes from taxSummary).
                continue;
            }
            // Amount.
            BigDecimal amount = bigDecimalOf(charge, "amount");
            // Charge code.
            String chargeCode = String.valueOf(charge.get("chargeCode"));
            // Route by code:
            switch (chargeCode) {
                // fuel,
                case "FUEL_SURCHARGE" -> surcharge = surcharge.add(amount);
                // insurance,
                case "INSURANCE" -> insuranceRov = insuranceRov.add(amount); // TODO: confirm code name
                // ODA,
                case "ODA" -> odaCharge = odaCharge.add(amount); // TODO: confirm code name
                // anything else -> handling.
                default -> handlingCharges = handlingCharges.add(amount);
            }
        }

        // RateResponse:
        return new RateResponse(
                // partner code,
                partnerCode(),
                // name,
                partnerName(),
                // base rate,
                bigDecimalOf(pricing, "baseRate"),
                // fuel hike 0,
                BigDecimal.ZERO, // fuelHike: ShreeMurti has no separate hike-vs-surcharge split
                // fuel surcharge,
                surcharge,
                // insurance,
                insuranceRov,
                // ODA,
                odaCharge,
                // handling,
                handlingCharges,
                // partner GST (taxSummary.totalTax),
                bigDecimalOf(taxSummary, "totalTax"),
                // partner total (calculation.totalAmount).
                bigDecimalOf(calculation, "totalAmount")
        );
    }

    // Safe cast to Map.
    private Map<String, Object> asMap(Object value) {
        // Map or empty.
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    // Safe cast to list of maps.
    private List<Map<String, Object>> asListOfMaps(Object value) {
        // List or empty.
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    // Number at key as BigDecimal.
    private BigDecimal bigDecimalOf(Map<String, Object> map, String key) {
        // Value.
        Object value = map.get(key);
        // Missing?
        if (value == null) {
            // -> 0.
            return BigDecimal.ZERO;
        }
        // Parse.
        return new BigDecimal(value.toString());
    }
}
