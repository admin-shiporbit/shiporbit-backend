package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.exception.PartnerApiException;
import com.shiporbit.backend.rate.auth.CachingTokenProvider;
import com.shiporbit.backend.rate.auth.ShreeMurtiTokenFetcher;
import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.dto.response.RateResponse;
import com.shiporbit.backend.rate.routing.ShreeMurtiConfigProperties;
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
import java.util.List;
import java.util.Map;

@Component
public class ShreeMurtiPartnerClient implements DeliveryPartnerClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShreeMurtiPartnerClient.class);
    // Pre-serializing the body to a String (below) instead of handing the raw Map to
    // .body(...) forces Spring to route it through StringHttpMessageConverter instead
    // of the Jackson message converter. The Jackson converter streams JSON straight to
    // the connection's OutputStream without knowing the byte length up front, which
    // makes the JDK-based ClientHttpRequestFactory send it as Transfer-Encoding:
    // chunked. StringHttpMessageConverter knows the length up front and sets
    // Content-Length instead. This matters here specifically because ShreeMurti's
    // gateway (nginx in front of it, going by the stock "400 Bad Request" HTML page)
    // rejects the chunked request outright - which is exactly why the identical
    // payload succeeds when run as a curl (curl always sends Content-Length) but 400s
    // when sent by this client.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Named to match the @Bean method name in ShreeMurtiClientConfig (shreeMurtiClient) -
    // there are now two RestClient beans in the context (delhiveryClient, shreeMurtiClient),
    // so Spring needs the parameter name to disambiguate by bean name.
    private final RestClient shreeMurtiClient;
    private final ShreeMurtiConfigProperties shreeMurtiConfigProperties;
    private final CachingTokenProvider tokenProvider;

    public ShreeMurtiPartnerClient(RestClient shreeMurtiClient,
                                   ShreeMurtiConfigProperties shreeMurtiConfigProperties,
                                   ShreeMurtiTokenFetcher shreeMurtiTokenFetcher) {
        this.shreeMurtiClient = shreeMurtiClient;
        this.shreeMurtiConfigProperties = shreeMurtiConfigProperties;
        // CachingTokenProvider is a plain object (not a @Component) on purpose: each
        // partner gets its own instance wrapping its own TokenFetcher. Built here so
        // ShreeMurti's token cache is scoped to this client.
        this.tokenProvider = new CachingTokenProvider(shreeMurtiTokenFetcher);
    }

    @Override
    public String partnerCode() {
        return "ShreeMurti";
    }

    @Override
    public String partnerName() {
        return "ShreeMurti";
    }

    @Override
    public boolean isServiceable(RequestParamRecord request) {
        return true;
    }

    @Override
    public RateResponse getRate(RequestParamRecord request) {
        LOGGER.debug("Fetching ShreeMurti rate: {} -> {}, weight={}", request.sourcePinCode(), request.destinationPinCode(), request.weight());
        String token = tokenProvider.getValidToken().get("id_token");
        String tenantId = tokenProvider.getValidToken().get("tenantId");
        Map<String, Object> requestBody = buildRequestBody(request);
        String url = shreeMurtiConfigProperties.routerUrl() + shreeMurtiConfigProperties.rateCalculatorEndpoint();
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
        Map<String, String> headers = Map.of(
                "Content-Type", "application/json",
                "Accept", "application/json",
                "Authorization", "Bearer " + token,
                "tenantid", tenantId);
        LOGGER.debug("Outgoing ShreeMurti rate-calculator request:\n{}", CurlLogger.toCurl("POST", url, headers, requestBody));
        String jsonBody = OBJECT_MAPPER.writeValueAsString(requestBody);
        Map<String, Object> response;
        try {
            response = shreeMurtiClient.post()
                    .uri(shreeMurtiConfigProperties.rateCalculatorEndpoint())
                    .headers(h -> headers.forEach(h::add))
                    .body(jsonBody)
                    .retrieve()
                    .body(Map.class);
        } catch (HttpStatusCodeException e) {
            throw PartnerApiException.from(e, "ShreeMurti");
        } catch (RestClientException e) {
            throw new PartnerApiException("Unable to connect to ShreeMurti rate-calculator API", e, HttpStatus.BAD_GATEWAY.value());
        }
        RateResponse rateResponse = mapToRateResponse(response);
        LOGGER.debug("ShreeMurti rate fetched: finalFreight={}", rateResponse.finalFreight());
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
    private static final double GRAMS_PER_KG = 1000.0;

    private Map<String, Object> buildRequestBody(RequestParamRecord request) {
        Map<String, Object> partnerOptions = request.partnerOptions();

        boolean cod = (boolean) partnerOptions.getOrDefault("cod", false);
        String deliveryMode = (String) partnerOptions.getOrDefault("deliveryMode", "SURFACE");
        double insuranceAmount = ((Number) partnerOptions.getOrDefault("insuranceAmount", 0.0)).doubleValue();

        Map<String, Object> insurance = Map.of(
                "enabled", request.isRovInsurance(),
                "amount", insuranceAmount
        );

        Map<String, Object> userOptions = Map.of(
                "insurance", insurance,
                "cod", cod
        );

        Map<String, Object> filters = Map.of(
                "delivery_mode", deliveryMode
        );

        double weightKg = request.weight() / GRAMS_PER_KG;

        return Map.ofEntries(
                Map.entry("fromPincode", Integer.parseInt(request.sourcePinCode())),
                Map.entry("toPincode", Integer.parseInt(request.destinationPinCode())),
                Map.entry("serviceType", "ECOMM"),
                Map.entry("productType", "ECOMM"),
                Map.entry("weight", weightKg),
                Map.entry("length", request.dimension().getLength()),
                Map.entry("height", request.dimension().getHeight()),
                Map.entry("width", request.dimension().getWidth()),
                Map.entry("includeDefaultCharges", false),
                Map.entry("userOptions", userOptions),
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
    private RateResponse mapToRateResponse(Map<String, Object> response) {
        Map<String, Object> data = asMap(response.get("data"));
        Map<String, Object> pricing = asMap(data.get("pricing"));
        Map<String, Object> calculation = asMap(data.get("calculation"));
        Map<String, Object> taxSummary = asMap(data.get("taxSummary"));
        List<Map<String, Object>> charges = asListOfMaps(pricing.get("charges"));

        BigDecimal surcharge = BigDecimal.ZERO;
        BigDecimal insuranceRov = BigDecimal.ZERO;
        BigDecimal odaCharge = BigDecimal.ZERO;
        BigDecimal handlingCharges = BigDecimal.ZERO;

        for (Map<String, Object> charge : charges) {
            if (Boolean.TRUE.equals(charge.get("isTax"))) {
                // Already captured via taxSummary.totalTax below - skip so it isn't
                // double-counted into one of the charge buckets.
                continue;
            }
            BigDecimal amount = bigDecimalOf(charge, "amount");
            String chargeCode = String.valueOf(charge.get("chargeCode"));
            switch (chargeCode) {
                case "FUEL_SURCHARGE" -> surcharge = surcharge.add(amount);
                case "INSURANCE" -> insuranceRov = insuranceRov.add(amount); // TODO: confirm code name
                case "ODA" -> odaCharge = odaCharge.add(amount); // TODO: confirm code name
                default -> handlingCharges = handlingCharges.add(amount);
            }
        }

        return new RateResponse(
                partnerCode(),
                partnerName(),
                bigDecimalOf(pricing, "baseRate"),
                BigDecimal.ZERO, // fuelHike: ShreeMurti has no separate hike-vs-surcharge split
                surcharge,
                insuranceRov,
                odaCharge,
                handlingCharges,
                bigDecimalOf(taxSummary, "totalTax"),
                bigDecimalOf(calculation, "totalAmount")
        );
    }

    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private List<Map<String, Object>> asListOfMaps(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private BigDecimal bigDecimalOf(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.toString());
    }
}
