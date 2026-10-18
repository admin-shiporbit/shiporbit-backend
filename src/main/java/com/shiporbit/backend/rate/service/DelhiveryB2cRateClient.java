package com.shiporbit.backend.rate.service;

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
@Component
public class DelhiveryB2cRateClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(DelhiveryB2cRateClient.class);
    private static final String PT_COD = "COD";
    private static final String PT_PREPAID = "Pre-paid"; // per Delhivery sample curl

    private final RestClient delhiveryB2cClient;
    private final DelhiveryB2cConfigProperties props;
    private final ServiceabilityCache serviceabilityCache;

    public DelhiveryB2cRateClient(RestClient delhiveryB2cClient, DelhiveryB2cConfigProperties props,
                                  ServiceabilityCache serviceabilityCache) {
        this.delhiveryB2cClient = delhiveryB2cClient;
        this.props = props;
        this.serviceabilityCache = serviceabilityCache;
    }

    // ---- serviceability: GET /c/api/pin-codes/json/?filter_codes=<pin> ----

    /**
     * Origin must allow pickup; destination must allow COD (COD shipments) or prepaid. Embargoed pins fail.
     * Each pincode is fetched once and cached (ServiceabilityCache); the COD/prepaid rule is applied after,
     * so one cached answer serves both payment modes.
     */
    public Serviceability checkLane(RequestParamRecord request) {
        boolean cod = "cod".equalsIgnoreCase(request.paymentMode());
        Serviceability origin = evaluate(pinRecord(request.sourcePinCode()), request.sourcePinCode(), true, cod);
        Serviceability destination = evaluate(pinRecord(request.destinationPinCode()), request.destinationPinCode(), false, cod);
        return Serviceability.both(origin, destination);
    }

    /** Cached raw pin flags; when SERVICEABLE, reason carries "pickup=Y,cod=Y,..." for evaluate(). */
    private Serviceability pinRecord(String pin) {
        return serviceabilityCache.get("delhivery:b2c:pin:" + pin, () -> fetchPin(pin));
    }

    private Serviceability fetchPin(String pin) {
        if (props.apiToken() == null || props.apiToken().isBlank()) {
            return Serviceability.unknown("Delhivery B2C api-token not configured");
        }
        String path = props.pincodeEndpoint() + "?filter_codes=" + pin;
        Object response;
        try {
            response = delhiveryB2cClient.get().uri(path)
                    .header("Content-Type", "application/json")
                    .header("Authorization", authHeader())
                    .retrieve().body(Object.class);
        } catch (HttpStatusCodeException e) {
            LOGGER.warn("Delhivery B2C pincode API {} for {}: {}", e.getStatusCode(), pin, e.getResponseBodyAsString());
            return Serviceability.unknown("Delhivery B2C pincode API HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            LOGGER.warn("Delhivery B2C pincode API unreachable for {}: {}", pin, e.getMessage());
            return Serviceability.unknown("Delhivery B2C pincode API unreachable");
        }
        LOGGER.debug("Delhivery B2C pincode response for {}: {}", pin, response);
        return DelhiveryPinParser.parse(response, pin);
    }

    private Serviceability evaluate(Serviceability s, String pin, boolean origin, boolean cod) {
        if (s.status() != Serviceability.Status.SERVICEABLE) {
            return s;
        }
        String flags = s.reason();
        if (origin) {
            return DelhiveryPinParser.flag(flags, "pickup")
                    ? Serviceability.yes("origin " + pin + " pickup OK")
                    : Serviceability.no("Delhivery: no pickup at origin " + pin);
        }
        String key = cod ? "cod" : "pre_paid";
        return DelhiveryPinParser.flag(flags, key)
                ? Serviceability.yes("destination " + pin + " " + key + " OK")
                : Serviceability.no("Delhivery: " + (cod ? "COD" : "prepaid") + " not available at destination " + pin);
    }

    private String authHeader() {
        return props.apiToken().startsWith("Token ") ? props.apiToken() : "Token " + props.apiToken();
    }

    /** True when this shipment should be priced on B2C: dead weight below maxWeightKg (default 20 kg). */
    public boolean handles(RequestParamRecord request) {
        return isB2c(request.weight(), props.maxWeightKg());
    }

    static boolean isB2c(Double deadWeightGrams, double maxWeightKg) {
        return deadWeightGrams != null && deadWeightGrams < maxWeightKg * 1000;
    }

    public RateResponse getRate(RequestParamRecord request, String partnerCode, String partnerName) {
        if (props.apiToken() == null || props.apiToken().isBlank()) {
            throw new PartnerApiException("Delhivery B2C api-token is not configured (DELHIVERY_B2C_API_TOKEN)",
                    HttpStatus.SERVICE_UNAVAILABLE.value());
        }
        Map<String, Object> query = buildQuery(request);
        String path = UriComponentsBuilder.fromPath(props.ratecalculatorEndpoint())
                .queryParams(toMultiValue(query)).build().toUriString();
        String auth = authHeader();

        LOGGER.debug("Outgoing Delhivery B2C rate request (dead weight {}g < {}kg):\n{}", request.weight(),
                props.maxWeightKg(), CurlLogger.toCurl("GET", props.routerUrl() + path,
                        Map.of("Content-Type", "application/json", "Authorization", auth), null));
        Object response;
        try {
            response = delhiveryB2cClient.get()
                    .uri(path)
                    .header("Content-Type", "application/json")
                    .header("Authorization", auth)
                    .retrieve()
                    .body(Object.class);
        } catch (HttpStatusCodeException e) {
            throw PartnerApiException.from(e, "Delhivery B2C");
        } catch (RestClientException e) {
            throw new PartnerApiException("Unable to connect to Delhivery B2C invoice-charges API", e,
                    HttpStatus.BAD_GATEWAY.value());
        }
        LOGGER.debug("Delhivery B2C response: {}", response);
        RateResponse rate = DelhiveryB2cResponseMapper.map(response, partnerCode, partnerName);
        LOGGER.debug("Delhivery B2C rate fetched: finalFreight={}", rate.finalFreight());
        return rate;
    }

    Map<String, Object> buildQuery(RequestParamRecord request) {
        boolean cod = "cod".equalsIgnoreCase(request.paymentMode());
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("md", mode(request));
        q.put("ss", props.shipmentStatus());
        q.put("o_pin", request.sourcePinCode());
        q.put("d_pin", request.destinationPinCode());
        q.put("cgm", chargeableGrams(request, props.volumetricDivisor()));
        q.put("pt", cod ? PT_COD : PT_PREPAID);
        // Always sent; 0 for prepaid (matches Delhivery's sample curl).
        q.put("cod", cod ? request.partnerOptions().getOrDefault("codAmount", request.inventoryAmout()) : 0);
        return q;
    }

    /** partnerOptions.deliveryMode EXPRESS/AIR -> E, anything else -> configured default (S). */
    private String mode(RequestParamRecord request) {
        Object m = request.partnerOptions().get("deliveryMode");
        if (m != null && ("EXPRESS".equalsIgnoreCase(m.toString()) || "AIR".equalsIgnoreCase(m.toString()))) {
            return "E";
        }
        return props.defaultMode();
    }

    /** max(dead weight, volumetric) in whole grams, rounded up. The API takes no dimensions, so we send chargeable. */
    static long chargeableGrams(RequestParamRecord request, int volumetricDivisor) {
        double dead = request.weight() == null ? 0 : request.weight();
        double volumetric = 0;
        var d = request.dimension();
        if (d != null && d.getLength() != null && d.getWidth() != null && d.getHeight() != null) {
            int boxes = Math.max(1, d.getBoxCount());
            volumetric = d.getLength() * d.getWidth() * d.getHeight() / volumetricDivisor * 1000 * boxes;
        }
        return (long) Math.ceil(Math.max(dead, volumetric));
    }

    private static org.springframework.util.MultiValueMap<String, String> toMultiValue(Map<String, Object> q) {
        var mv = new org.springframework.util.LinkedMultiValueMap<String, String>();
        q.forEach((k, v) -> mv.add(k, String.valueOf(v)));
        return mv;
    }
}
