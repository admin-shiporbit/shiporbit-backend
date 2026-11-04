package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.exception.PartnerApiException;
import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.dto.response.RateResponse;
import com.shiporbit.backend.rate.routing.DhlConfigProperties;
import com.shiporbit.backend.rate.util.CurlLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * DHL Express MyDHL API - POST /rates (multi-piece capable).
 * Spec: https://developer.dhl.com/api-reference/dhl-express-mydhl-api
 *
 * DHL Express from India is international only (domestic is Blue Dart), so this
 * partner is serviceable only for a non-IN destinationCountry with a destinationCity
 * (MyDHL requires cityName for both shipper and receiver).
 *
 * partnerOptions keys read by this client (shared keys match UpsPartnerClient):
 *  - destinationCountry    (required, ISO-2, e.g. "US")
 *  - destinationCity       (required)
 *  - destinationPostalCode (defaults to request.destinationPinCode())
 *  - originCity            (defaults to rate-aggregator.dhl.shipper-city)
 *  - documents             (boolean, default false - true means not customs declarable)
 *  - dhlProductCode        (e.g. "P" Express Worldwide). If set, only that product is
 *                          returned; otherwise the cheapest product is returned.
 */
@Component
public class DhlPartnerClient implements DeliveryPartnerClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(DhlPartnerClient.class);
    private static final String ORIGIN_COUNTRY = "IN";
    private static final ZoneId ORIGIN_ZONE = ZoneId.of("Asia/Kolkata");
    private static final double GRAMS_PER_KG = 1000.0;
    // Billing currency - the amount DHL will invoice the account in.
    private static final String BILLING_CURRENCY_TYPE = "BILLC";

    private final RestClient dhlClient;
    private final DhlConfigProperties dhlConfigProperties;

    public DhlPartnerClient(RestClient dhlClient, DhlConfigProperties dhlConfigProperties) {
        this.dhlClient = dhlClient;
        this.dhlConfigProperties = dhlConfigProperties;
    }

    @Override
    public String partnerCode() {
        return "dhl";
    }

    @Override
    public String partnerName() {
        return "DHL Express";
    }

    @Override
    public boolean isServiceable(RequestParamRecord request) {
        if (!dhlConfigProperties.isConfigured()) {
            LOGGER.debug("DHL skipped: api key/secret/account number not configured");
            return false;
        }
        String destinationCountry = destinationCountry(request);
        return destinationCountry != null
                && !ORIGIN_COUNTRY.equals(destinationCountry)
                && option(request, "destinationCity", null) != null;
    }

    @Override
    public RateResponse getRate(RequestParamRecord request) {
        LOGGER.debug("Fetching DHL rate: {} -> {}, weight={}g",
                request.sourcePinCode(), destinationCountry(request), request.weight());

        Map<String, Object> requestBody = buildRequestBody(request);
        String messageReference = UUID.randomUUID().toString();
        LOGGER.debug("Outgoing DHL rates request:\n{}", CurlLogger.toCurl("POST",
                dhlConfigProperties.routerUrl() + dhlConfigProperties.ratesEndpoint(),
                Map.of("Content-Type", "application/json", "Message-Reference", messageReference,
                        "Authorization", "Basic ***REDACTED***"),
                requestBody));

        Map response;
        try {
            response = dhlClient.post()
                    .uri(dhlConfigProperties.ratesEndpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Message-Reference", messageReference)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);
        } catch (HttpStatusCodeException e) {
            throw PartnerApiException.from(e, "DHL");
        } catch (RestClientException e) {
            throw new PartnerApiException("Unable to connect to DHL rates API", e, HttpStatus.BAD_GATEWAY.value());
        }

        RateResponse rateResponse = mapToRateResponse(response, option(request, "dhlProductCode", null));
        LOGGER.debug("DHL rate fetched: finalFreight={}", rateResponse.finalFreight());
        return rateResponse;
    }

    private Map<String, Object> buildRequestBody(RequestParamRecord request) {
        Map<String, Object> shipper = new LinkedHashMap<>();
        shipper.put("postalCode", request.sourcePinCode());
        shipper.put("cityName", option(request, "originCity", dhlConfigProperties.shipperCity()));
        shipper.put("countryCode", ORIGIN_COUNTRY);

        Map<String, Object> receiver = new LinkedHashMap<>();
        receiver.put("postalCode", option(request, "destinationPostalCode", request.destinationPinCode()));
        receiver.put("cityName", option(request, "destinationCity", null));
        receiver.put("countryCode", destinationCountry(request));

        boolean documents = Boolean.TRUE.equals(request.partnerOptions().get("documents"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerDetails", Map.of("shipperDetails", shipper, "receiverDetails", receiver));
        body.put("accounts", List.of(Map.of("typeCode", "shipper", "number", dhlConfigProperties.accountNumber())));
        body.put("plannedShippingDateAndTime", nextPickupDateTime());
        body.put("unitOfMeasurement", "metric");
        body.put("isCustomsDeclarable", !documents);
        body.put("packages", buildPackages(request));
        return body;
    }

    // RequestParamRecord.weight() is the total shipment weight in GRAMS; split evenly
    // across boxCount identical boxes since DHL rates per piece.
    private List<Map<String, Object>> buildPackages(RequestParamRecord request) {
        var dimension = request.dimension();
        int boxCount = Math.max(dimension.getBoxCount(), 1);
        BigDecimal weightPerBoxKg = BigDecimal.valueOf(request.weight() / GRAMS_PER_KG / boxCount)
                .setScale(3, RoundingMode.HALF_UP);

        Map<String, Object> piece = Map.of(
                "weight", weightPerBoxKg,
                "dimensions", Map.of(
                        "length", dimension.getLength(),
                        "width", dimension.getWidth(),
                        "height", dimension.getHeight()));
        List<Map<String, Object>> packages = new ArrayList<>(boxCount);
        for (int i = 0; i < boxCount; i++) {
            packages.add(piece);
        }
        return packages;
    }

    // MyDHL expects e.g. "2026-10-05T10:00:00GMT+05:30". Quote for the next working
    // day at 10:00 IST so a late-evening request isn't rejected as a past pickup.
    private String nextPickupDateTime() {
        LocalDate date = LocalDate.now(ORIGIN_ZONE).plusDays(1);
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return date + "T10:00:00GMT+05:30";
    }

    // Response shape per the MyDHL spec (not yet confirmed via a live call):
    // {products: [{productName, productCode,
    //   totalPrice: [{currencyType: BILLC|PULCL|BASEC, priceCurrency, price}],
    //   totalPriceBreakdown: [{currencyType, priceCurrency,
    //     priceBreakdown: [{typeCode, price}]}],          // STTXA = total tax
    //   detailedPriceBreakdown: [{currencyType, priceCurrency,
    //     breakdown: [{name, serviceCode, price}]}],      // first item = product charge
    //   deliveryCapabilities: {...}}]}
    private RateResponse mapToRateResponse(Map<String, Object> response, String productCode) {
        List<Map<String, Object>> products = asList(response == null ? null : response.get("products"));
        if (productCode != null) {
            products = products.stream()
                    .filter(product -> productCode.equalsIgnoreCase(String.valueOf(product.get("productCode"))))
                    .toList();
        }
        if (products.isEmpty()) {
            throw new PartnerApiException("No DHL product available for the request", HttpStatus.BAD_GATEWAY.value());
        }

        Map<String, Object> cheapest = products.stream()
                .min(Comparator.comparing(this::billingTotal))
                .orElseThrow();
        LOGGER.debug("DHL selected product {} ({}) out of {} option(s)",
                cheapest.get("productCode"), cheapest.get("productName"), products.size());

        BigDecimal total = billingTotal(cheapest);

        BigDecimal gst = BigDecimal.ZERO;
        for (Map<String, Object> item : asList(billingEntry(asList(cheapest.get("totalPriceBreakdown"))).get("priceBreakdown"))) {
            if ("STTXA".equals(String.valueOf(item.get("typeCode")))) {
                gst = gst.add(decimal(item.get("price")));
            }
        }

        List<Map<String, Object>> breakdown = asList(billingEntry(asList(cheapest.get("detailedPriceBreakdown"))).get("breakdown"));
        BigDecimal base = breakdown.isEmpty() ? BigDecimal.ZERO : decimal(breakdown.get(0).get("price"));
        BigDecimal fuel = BigDecimal.ZERO;
        for (Map<String, Object> item : breakdown) {
            if (String.valueOf(item.get("name")).toUpperCase(Locale.ROOT).contains("FUEL")) {
                fuel = fuel.add(decimal(item.get("price")));
            }
        }
        if (base.signum() == 0) {
            base = total.subtract(gst).subtract(fuel);
        }
        BigDecimal handling = total.subtract(gst).subtract(fuel).subtract(base).max(BigDecimal.ZERO);

        return new RateResponse(
                partnerCode(),
                partnerName(),
                base,
                BigDecimal.ZERO,
                fuel,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                handling,
                gst,
                total
        );
    }

    private BigDecimal billingTotal(Map<String, Object> product) {
        return decimal(billingEntry(asList(product.get("totalPrice"))).get("price"));
    }

    // Prefer the billing-currency entry; fall back to the first one returned.
    private Map<String, Object> billingEntry(List<Map<String, Object>> entries) {
        return entries.stream()
                .filter(entry -> BILLING_CURRENCY_TYPE.equals(entry.get("currencyType")))
                .findFirst()
                .orElse(entries.isEmpty() ? Map.of() : entries.get(0));
    }

    private String destinationCountry(RequestParamRecord request) {
        String country = option(request, "destinationCountry", null);
        return country == null ? null : country.trim().toUpperCase(Locale.ROOT);
    }

    private String option(RequestParamRecord request, String key, String defaultValue) {
        Object value = request.partnerOptions().get(key);
        return value == null || value.toString().isBlank() ? defaultValue : value.toString();
    }

    private BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    private List<Map<String, Object>> asList(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        }
        return List.of();
    }
}
