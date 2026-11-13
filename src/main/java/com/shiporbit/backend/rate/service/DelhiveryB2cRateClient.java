package com.shiporbit.backend.rate.service;

// Imports: RestClient, B2C config, serviceability cache and parser, error types, curl logger.
import com.shiporbit.backend.error.ErrorCode;
import com.shiporbit.backend.exception.PartnerApiException;
import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.dto.response.RateResponse;
import com.shiporbit.backend.rate.routing.DelhiveryB2cConfigProperties;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Delhivery B2C (express parcel) rate call. Deliberately NOT a DeliveryPartnerClient: Delhivery is one
 * partner (id 1); DelhiveryPartnerClient decides per request whether to call this or the LTL API.
 * <p>
 * GET {routerUrl}/api/kinko/v1/invoice/charges/.json?md=S&ss=Delivered&o_pin=..&d_pin=..&cgm=..&pt=COD&cod=..
 * Header: Authorization: Token {apiToken}
 */
// Spring bean, used only by DelhiveryPartnerClient.
@Component
public class DelhiveryB2cRateClient {

    // Logger.
    private static final Logger LOGGER = LoggerFactory.getLogger(DelhiveryB2cRateClient.class);
    // pt value for COD.
    private static final String PT_COD = "COD";
    // pt value for prepaid.
    private static final String PT_PREPAID = "Pre-paid"; // per Delhivery sample curl

    // RestClient with the B2C base URL.
    private final RestClient delhiveryB2cClient;
    // B2C config: router URL, endpoints, api-token, max weight, divisor, defaults.
    private final DelhiveryB2cConfigProperties props;
    // 12 h pincode answer cache.
    private final ServiceabilityCache serviceabilityCache;

    // Constructor injection.
    public DelhiveryB2cRateClient(RestClient delhiveryB2cClient, DelhiveryB2cConfigProperties props,
                                  ServiceabilityCache serviceabilityCache) {
        // Store.
        this.delhiveryB2cClient = delhiveryB2cClient;
        // Store.
        this.props = props;
        // Store.
        this.serviceabilityCache = serviceabilityCache;
    }

    // ---- serviceability: GET /c/api/pin-codes/json/?filter_codes=<pin> ----

    /**
     * Origin must allow pickup; destination must allow COD (COD shipments) or prepaid. Embargoed pins fail.
     * Each pincode is fetched once and cached (ServiceabilityCache); the COD/prepaid rule is applied after,
     * so one cached answer serves both payment modes.
     */
    // B2C serviceability for the lane.
    public Serviceability checkLane(RequestParamRecord request) {
        // COD shipment?
        boolean cod = "cod".equalsIgnoreCase(request.paymentMode());
        // Origin: cached pin flags, must allow pickup.
        Serviceability origin = evaluate(pinRecord(request.sourcePinCode()), request.sourcePinCode(), true, cod);
        // Destination: cached pin flags, must allow COD (COD) or pre_paid (prepaid).
        Serviceability destination = evaluate(pinRecord(request.destinationPinCode()), request.destinationPinCode(), false, cod);
        // Combine: both must be SERVICEABLE.
        return Serviceability.both(origin, destination);
    }

    /** Cached raw pin flags; when SERVICEABLE, reason carries "pickup=Y,cod=Y,..." for evaluate(). */
    // One pincode's raw flags, cached per pincode;
    private Serviceability pinRecord(String pin) {
        // fetchPin() runs only on a cache miss.
        return serviceabilityCache.get("delhivery:b2c:pin:" + pin, () -> fetchPin(pin));
    }

    // Call the B2C pin-codes API.
    private Serviceability fetchPin(String pin) {
        // No api-token configured (DELHIVERY_B2C_API_TOKEN)?
        if (props.apiToken() == null || props.apiToken().isBlank()) {
            // -> UNKNOWN PARTNER_NOT_CONFIGURED (rate NOT fetched).
            return Serviceability.unknown(ErrorCode.PARTNER_NOT_CONFIGURED, "Delhivery B2C api-token not configured");
        }
        // GET pin-codes/json/?filter_codes={pin}.
        String path = props.pincodeEndpoint() + "?filter_codes=" + pin;
        // Raw answer.
        Object response;
        // Try:
        try {
            // GET
            response = delhiveryB2cClient.get().uri(path)
                    // JSON,
                    .header("Content-Type", "application/json")
                    // "Token <api-token>",
                    .header("Authorization", authHeader())
                    // read as generic JSON.
                    .retrieve().body(Object.class);
        // HTTP error (e.g. 401 bad token):
        } catch (HttpStatusCodeException e) {
            // log warning,
            LOGGER.warn("Delhivery B2C pincode API {} for {}: {}", e.getStatusCode(), pin, e.getResponseBodyAsString());
            // -> UNKNOWN with an error code for the status.
            return Serviceability.unknown(Serviceability.codeForHttp(e.getStatusCode().value()), "Delhivery B2C pincode API HTTP " + e.getStatusCode().value());
        // Unreachable:
        } catch (RestClientException e) {
            // log warning,
            LOGGER.warn("Delhivery B2C pincode API unreachable for {}: {}", pin, e.getMessage());
            // -> UNKNOWN.
            return Serviceability.unknown(PartnerApiException.codeForCause(e), "Delhivery B2C pincode API unreachable");
        }
        // Debug log of the answer.
        LOGGER.debug("Delhivery B2C pincode response for {}: {}", pin, response);
        // Parse delivery_codes -> SERVICEABLE with "pickup=Y,cod=Y,pre_paid=Y,..." in reason, NOT_SERVICEABLE (no record / embargo) or UNKNOWN.
        return DelhiveryPinParser.parse(response, pin);
    }

    // Apply the origin / destination rule to a cached pin answer.
    private Serviceability evaluate(Serviceability s, String pin, boolean origin, boolean cod) {
        // Not SERVICEABLE (no / unknown)?
        if (s.status() != Serviceability.Status.SERVICEABLE) {
            // Return it as is.
            return s;
        }
        // Raw flags text.
        String flags = s.reason();
        // Origin?
        if (origin) {
            // pickup flag Y
            return DelhiveryPinParser.flag(flags, "pickup")
                    // -> SERVICEABLE,
                    ? Serviceability.yes("origin " + pin + " pickup OK")
                    // else NOT_SERVICEABLE PICKUP_NOT_AVAILABLE.
                    : Serviceability.no(ErrorCode.PICKUP_NOT_AVAILABLE, "Delhivery: no pickup at origin " + pin);
        }
        // Destination: flag to check (cod or pre_paid).
        String key = cod ? "cod" : "pre_paid";
        // Flag Y
        return DelhiveryPinParser.flag(flags, key)
                // -> SERVICEABLE,
                ? Serviceability.yes("destination " + pin + " " + key + " OK")
                // else NOT_SERVICEABLE COD / PREPAID_NOT_SERVICEABLE.
                : Serviceability.no(cod ? ErrorCode.COD_NOT_SERVICEABLE : ErrorCode.PREPAID_NOT_SERVICEABLE, "Delhivery: " + (cod ? "COD" : "prepaid") + " not available at destination " + pin);
    }

    // Authorization header.
    private String authHeader() {
        // "Token xxx" (adds the "Token " prefix if missing).
        return props.apiToken().startsWith("Token ") ? props.apiToken() : "Token " + props.apiToken();
    }

    /** True when this shipment should be priced on B2C: dead weight below maxWeightKg (default 20 kg). */
    // B2C or LTL?
    public boolean handles(RequestParamRecord request) {
        // Dead weight below the B2C limit.
        return isB2c(request.weight(), props.maxWeightKg());
    }

    // Pure check (unit-tested).
    static boolean isB2c(Double deadWeightGrams, double maxWeightKg) {
        // grams < maxKg x 1000 (e.g. < 20,000 g).
        return deadWeightGrams != null && deadWeightGrams < maxWeightKg * 1000;
    }

    // B2C rate call (only after SERVICEABLE).
    public RateResponse getRate(RequestParamRecord request, String partnerCode, String partnerName) {
        // No api-token?
        if (props.apiToken() == null || props.apiToken().isBlank()) {
            // -> PartnerApiException 503 (-> Unavailable).
            throw new PartnerApiException("Delhivery B2C api-token is not configured (DELHIVERY_B2C_API_TOKEN)",
                    HttpStatus.SERVICE_UNAVAILABLE.value());
        }
        // Query parameters.
        Map<String, Object> query = buildQuery(request);
        // Path: invoice/charges/.json
        String path = UriComponentsBuilder.fromPath(props.ratecalculatorEndpoint())
                // + query string.
                .queryParams(toMultiValue(query)).build().toUriString();
        // Authorization header.
        String auth = authHeader();

        // Debug: request as a curl
        LOGGER.debug("Outgoing Delhivery B2C rate request (dead weight {}g < {}kg):\n{}", request.weight(),
                // (token masked by the log masker).
                props.maxWeightKg(), CurlLogger.toCurl("GET", props.routerUrl() + path,
                        Map.of("Content-Type", "application/json", "Authorization", auth), null));
        // Raw answer.
        Object response;
        // Try:
        try {
            // GET
            response = delhiveryB2cClient.get()
                    // path,
                    .uri(path)
                    // JSON,
                    .header("Content-Type", "application/json")
                    // Token header,
                    .header("Authorization", auth)
                    // send,
                    .retrieve()
                    // read as generic JSON.
                    .body(Object.class);
        // HTTP error:
        } catch (HttpStatusCodeException e) {
            // -> PartnerApiException.
            throw PartnerApiException.from(e, "Delhivery B2C");
        // Unreachable:
        } catch (RestClientException e) {
            // -> PartnerApiException 502.
            throw new PartnerApiException("Unable to connect to Delhivery B2C invoice-charges API", e,
                    HttpStatus.BAD_GATEWAY.value());
        }
        // Debug log of the answer.
        LOGGER.debug("Delhivery B2C response: {}", response);
        // Map to RateResponse (DelhiveryB2cResponseMapper).
        RateResponse rate = DelhiveryB2cResponseMapper.map(response, partnerCode, partnerName);
        // Debug: final freight.
        LOGGER.debug("Delhivery B2C rate fetched: finalFreight={}", rate.finalFreight());
        // Return.
        return rate;
    }

    // Query parameters for the rate call.
    Map<String, Object> buildQuery(RequestParamRecord request) {
        // COD?
        boolean cod = "cod".equalsIgnoreCase(request.paymentMode());
        // In order:
        Map<String, Object> q = new LinkedHashMap<>();
        // md: S (surface) or E (express),
        q.put("md", mode(request));
        // ss: shipment status (Delivered),
        q.put("ss", props.shipmentStatus());
        // o_pin: origin,
        q.put("o_pin", request.sourcePinCode());
        // d_pin: destination,
        q.put("d_pin", request.destinationPinCode());
        // cgm: chargeable grams (the API takes no dimensions),
        q.put("cgm", chargeableGrams(request, props.volumetricDivisor()));
        // pt: COD / Pre-paid,
        q.put("pt", cod ? PT_COD : PT_PREPAID);
        // Always sent; 0 for prepaid (matches Delhivery's sample curl).
        // cod: COD amount, 0 for prepaid.
        q.put("cod", cod ? request.partnerOptions().getOrDefault("codAmount", request.inventoryAmout()) : 0);
        // Done.
        return q;
    }

    /** partnerOptions.deliveryMode EXPRESS/AIR -> E, anything else -> configured default (S). */
    // md value.
    private String mode(RequestParamRecord request) {
        // Requested mode option.
        Object m = request.partnerOptions().get("deliveryMode");
        // EXPRESS or AIR?
        if (m != null && ("EXPRESS".equalsIgnoreCase(m.toString()) || "AIR".equalsIgnoreCase(m.toString()))) {
            // -> E.
            return "E";
        }
        // Else configured default (S).
        return props.defaultMode();
    }

    /** max(dead weight, volumetric) in whole grams, rounded up. The API takes no dimensions, so we send chargeable. */
    // Chargeable grams sent as cgm.
    static long chargeableGrams(RequestParamRecord request, int volumetricDivisor) {
        // Dead weight (g).
        double dead = request.weight() == null ? 0 : request.weight();
        // Volumetric (g), 0 when no dimensions.
        double volumetric = 0;
        // Box dimensions.
        var d = request.dimension();
        // All three present?
        if (d != null && d.getLength() != null && d.getWidth() != null && d.getHeight() != null) {
            // At least one box.
            int boxes = Math.max(1, d.getBoxCount());
            // L x W x H / divisor (kg) x 1000 (g) x boxes.
            volumetric = d.getLength() * d.getWidth() * d.getHeight() / volumetricDivisor * 1000 * boxes;
        }
        // max(dead, volumetric), rounded up to a whole gram.
        return (long) Math.ceil(Math.max(dead, volumetric));
    }

    // Map -> MultiValueMap for the URI builder.
    private static org.springframework.util.MultiValueMap<String, String> toMultiValue(Map<String, Object> q) {
        // New map.
        var mv = new org.springframework.util.LinkedMultiValueMap<String, String>();
        // Each value as a string.
        q.forEach((k, v) -> mv.add(k, String.valueOf(v)));
        // Done.
        return mv;
    }
}
