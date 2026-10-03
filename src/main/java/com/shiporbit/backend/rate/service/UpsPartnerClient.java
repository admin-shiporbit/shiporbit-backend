package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.exception.PartnerApiException;
import com.shiporbit.backend.rate.auth.CachingTokenProvider;
import com.shiporbit.backend.rate.auth.UpsTokenFetcher;
import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.dto.response.RateResponse;
import com.shiporbit.backend.rate.routing.UpsConfigProperties;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * UPS Rating API - POST /api/rating/{version}/{requestoption}
 * Spec: https://developer.ups.com/tag/Rating?loc=en_US
 * (OpenAPI: github.com/UPS-API/api-documentation/blob/main/Rating.yaml)
 *
 * UPS from India is international express only, so this partner is serviceable only
 * when the caller names a destination country other than IN in partnerOptions.
 *
 * partnerOptions keys read by this client:
 *  - destinationCountry     (required, ISO-2, e.g. "US")
 *  - destinationPostalCode  (defaults to request.destinationPinCode())
 *  - destinationCity        (required by UPS for countries without postal codes)
 *  - destinationState       (2-char state/province code, e.g. "NY")
 *  - destinationAddressLine (defaults to "NA")
 *  - residential            (boolean, default false)
 *  - serviceCode            (e.g. "65" Saver, "07" Express). If set, rates only that
 *                           service ("Rate"); otherwise shops all services ("Shop")
 *                           and returns the cheapest.
 *  - originCity             (defaults to rate-aggregator.ups.shipper-city)
 */
@Component
public class UpsPartnerClient implements DeliveryPartnerClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpsPartnerClient.class);
    private static final String ORIGIN_COUNTRY = "IN";
    private static final double GRAMS_PER_KG = 1000.0;
    private static final int MAX_PACKAGES = 200;
    private static final String FUEL_SURCHARGE_CODE = "375";

    private final RestClient upsClient;
    private final UpsConfigProperties upsConfigProperties;
    private final CachingTokenProvider tokenProvider;

    public UpsPartnerClient(RestClient upsClient,
                            UpsConfigProperties upsConfigProperties,
                            UpsTokenFetcher upsTokenFetcher) {
        this.upsClient = upsClient;
        this.upsConfigProperties = upsConfigProperties;
        this.tokenProvider = new CachingTokenProvider(upsTokenFetcher);
    }

    @Override
    public String partnerCode() {
        return "ups";
    }

    @Override
    public String partnerName() {
        return "UPS";
    }

    @Override
    public boolean isServiceable(RequestParamRecord request) {
        if (!upsConfigProperties.isConfigured()) {
            LOGGER.debug("UPS skipped: client id/secret not configured");
            return false;
        }
        String destinationCountry = destinationCountry(request);
        return destinationCountry != null && !ORIGIN_COUNTRY.equals(destinationCountry);
    }

    @Override
    public RateResponse getRate(RequestParamRecord request) {
        String serviceCode = option(request, "serviceCode", null);
        String requestOption = serviceCode == null ? "Shop" : "Rate";
        LOGGER.debug("Fetching UPS rate ({}): {} -> {}, weight={}g", requestOption,
                request.sourcePinCode(), destinationCountry(request), request.weight());

        String token = tokenProvider.getValidToken().get("token");
        Map<String, Object> requestBody = buildRequestBody(request, requestOption, serviceCode);
        String transId = UUID.randomUUID().toString().replace("-", "");

        String path = upsConfigProperties.ratingEndpoint()
                .replace("{version}", upsConfigProperties.apiVersion())
                .replace("{requestoption}", requestOption);
        LOGGER.debug("Outgoing UPS rating request:\n{}", CurlLogger.toCurl("POST",
                upsConfigProperties.routerUrl() + path,
                Map.of("Content-Type", "application/json", "transId", transId,
                        "transactionSrc", upsConfigProperties.transactionSource(),
                        "Authorization", "Bearer " + token),
                requestBody));

        Map response;
        try {
            response = upsClient.post()
                    .uri(upsConfigProperties.ratingEndpoint(), upsConfigProperties.apiVersion(), requestOption)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .header("transId", transId)
                    .header("transactionSrc", upsConfigProperties.transactionSource())
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);
        } catch (HttpStatusCodeException e) {
            throw PartnerApiException.from(e, "UPS");
        } catch (RestClientException e) {
            throw new PartnerApiException("Unable to connect to UPS Rating API", e, HttpStatus.BAD_GATEWAY.value());
        }

        RateResponse rateResponse = mapToRateResponse(response);
        LOGGER.debug("UPS rate fetched: finalFreight={}", rateResponse.finalFreight());
        return rateResponse;
    }

    private Map<String, Object> buildRequestBody(RequestParamRecord request, String requestOption, String serviceCode) {
        Map<String, Object> shipperAddress = new LinkedHashMap<>();
        shipperAddress.put("AddressLine", List.of(upsConfigProperties.shipperAddressLine()));
        shipperAddress.put("City", option(request, "originCity", upsConfigProperties.shipperCity()));
        shipperAddress.put("PostalCode", request.sourcePinCode());
        shipperAddress.put("CountryCode", ORIGIN_COUNTRY);

        Map<String, Object> shipper = new LinkedHashMap<>();
        shipper.put("Name", upsConfigProperties.shipperName());
        if (upsConfigProperties.hasAccountNumber()) {
            shipper.put("ShipperNumber", upsConfigProperties.accountNumber());
        }
        shipper.put("Address", shipperAddress);

        Map<String, Object> shipToAddress = new LinkedHashMap<>();
        shipToAddress.put("AddressLine", List.of(option(request, "destinationAddressLine", "NA")));
        putIfPresent(shipToAddress, "City", option(request, "destinationCity", null));
        putIfPresent(shipToAddress, "StateProvinceCode", option(request, "destinationState", null));
        shipToAddress.put("PostalCode", option(request, "destinationPostalCode", request.destinationPinCode()));
        shipToAddress.put("CountryCode", destinationCountry(request));
        if (Boolean.TRUE.equals(request.partnerOptions().get("residential"))) {
            shipToAddress.put("ResidentialAddressIndicator", "");
        }

        Map<String, Object> shipment = new LinkedHashMap<>();
        shipment.put("Shipper", shipper);
        shipment.put("ShipTo", Map.of("Name", "Consignee", "Address", shipToAddress));
        shipment.put("ShipFrom", Map.of("Name", upsConfigProperties.shipperName(), "Address", shipperAddress));
        if (upsConfigProperties.hasAccountNumber()) {
            shipment.put("PaymentDetails", Map.of("ShipmentCharge", List.of(Map.of(
                    "Type", "01",
                    "BillShipper", Map.of("AccountNumber", upsConfigProperties.accountNumber())))));
            shipment.put("ShipmentRatingOptions", Map.of("NegotiatedRatesIndicator", "Y"));
        }
        if (serviceCode != null) {
            shipment.put("Service", Map.of("Code", serviceCode));
        }
        shipment.put("Package", buildPackages(request));

        Map<String, Object> rateRequest = new LinkedHashMap<>();
        rateRequest.put("Request", Map.of(
                "RequestOption", requestOption,
                "TransactionReference", Map.of("CustomerContext", "shiporbit-rate")));
        rateRequest.put("Shipment", shipment);
        return Map.of("RateRequest", rateRequest);
    }

    // RequestParamRecord.weight() is the total shipment weight in GRAMS; it's split
    // evenly across boxCount identical boxes because UPS rates per package.
    private List<Map<String, Object>> buildPackages(RequestParamRecord request) {
        var dimension = request.dimension();
        int boxCount = Math.min(Math.max(dimension.getBoxCount(), 1), MAX_PACKAGES);
        String weightPerBoxKg = String.format(Locale.ROOT, "%.2f", request.weight() / GRAMS_PER_KG / boxCount);

        Map<String, Object> pkg = Map.of(
                "PackagingType", Map.of("Code", "02"),
                "Dimensions", Map.of(
                        "UnitOfMeasurement", Map.of("Code", "CM"),
                        "Length", String.valueOf(dimension.getLength()),
                        "Width", String.valueOf(dimension.getWidth()),
                        "Height", String.valueOf(dimension.getHeight())),
                "PackageWeight", Map.of(
                        "UnitOfMeasurement", Map.of("Code", "KGS"),
                        "Weight", weightPerBoxKg));
        List<Map<String, Object>> packages = new ArrayList<>(boxCount);
        for (int i = 0; i < boxCount; i++) {
            packages.add(pkg);
        }
        return packages;
    }

    // Response shape per the UPS OpenAPI spec (not yet confirmed via a live call):
    // {RateResponse: {Response: {...}, RatedShipment: <object for Rate | array for Shop>
    //   {Service: {Code}, BaseServiceCharge, TransportationCharges, ServiceOptionsCharges,
    //    ItemizedCharges: [{Code, MonetaryValue}], TaxCharges: [{Type, MonetaryValue}],
    //    TotalCharges, TotalChargesWithTaxes,
    //    NegotiatedRateCharges: {BaseServiceCharge, ItemizedCharges, TaxCharges,
    //                            TotalCharge, TotalChargesWithTaxes}}}}
    // Money fields are {CurrencyCode, MonetaryValue: "123.45"}. Currency follows the
    // shipper account (INR for an Indian account).
    private RateResponse mapToRateResponse(Map<String, Object> response) {
        Map<String, Object> rateResponse = asMap(response == null ? null : response.get("RateResponse"));
        List<Map<String, Object>> ratedShipments = asList(rateResponse.get("RatedShipment"));
        if (ratedShipments.isEmpty()) {
            throw new PartnerApiException("No rated shipment in the UPS response", HttpStatus.BAD_GATEWAY.value());
        }

        Map<String, Object> cheapest = ratedShipments.stream()
                .min(Comparator.comparing(this::finalCharge))
                .orElseThrow();
        LOGGER.debug("UPS selected service {} out of {} option(s)",
                asMap(cheapest.get("Service")).get("Code"), ratedShipments.size());

        Map<String, Object> negotiated = asMap(cheapest.get("NegotiatedRateCharges"));
        boolean useNegotiated = !negotiated.isEmpty();

        BigDecimal total = finalCharge(cheapest);
        BigDecimal gst = sumMoney(asList(useNegotiated ? negotiated.get("TaxCharges") : cheapest.get("TaxCharges")));
        List<Map<String, Object>> itemized = asList(useNegotiated ? negotiated.get("ItemizedCharges") : cheapest.get("ItemizedCharges"));
        BigDecimal fuel = BigDecimal.ZERO;
        for (Map<String, Object> charge : itemized) {
            if (FUEL_SURCHARGE_CODE.equals(String.valueOf(charge.get("Code")))) {
                fuel = fuel.add(money(charge));
            }
        }

        Map<String, Object> baseContainer = useNegotiated ? negotiated : cheapest;
        BigDecimal base = money(asMap(baseContainer.get("BaseServiceCharge")));
        if (base.signum() == 0) {
            // No base breakdown returned - treat everything pre-tax except fuel as base.
            base = total.subtract(gst).subtract(fuel);
        }
        // Whatever remains (other itemized surcharges, service options) is handling.
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

    // Negotiated (contract) rate wins when present; tax-inclusive total wins over pre-tax.
    private BigDecimal finalCharge(Map<String, Object> ratedShipment) {
        Map<String, Object> negotiated = asMap(ratedShipment.get("NegotiatedRateCharges"));
        for (BigDecimal candidate : List.of(
                money(asMap(negotiated.get("TotalChargesWithTaxes"))),
                money(asMap(negotiated.get("TotalCharge"))),
                money(asMap(ratedShipment.get("TotalChargesWithTaxes"))),
                money(asMap(ratedShipment.get("TotalCharges"))))) {
            if (candidate.signum() > 0) {
                return candidate;
            }
        }
        return BigDecimal.ZERO;
    }

    private String destinationCountry(RequestParamRecord request) {
        String country = option(request, "destinationCountry", null);
        return country == null ? null : country.trim().toUpperCase();
    }

    private String option(RequestParamRecord request, String key, String defaultValue) {
        Object value = request.partnerOptions().get(key);
        return value == null || value.toString().isBlank() ? defaultValue : value.toString();
    }

    private void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private BigDecimal money(Map<String, Object> moneyNode) {
        Object value = moneyNode.get("MonetaryValue");
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    private BigDecimal sumMoney(List<Map<String, Object>> moneyNodes) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Map<String, Object> node : moneyNodes) {
            sum = sum.add(money(node));
        }
        return sum;
    }

    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    // UPS returns a single object instead of a one-element array in several places.
    private List<Map<String, Object>> asList(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?>) {
                    result.add(asMap(item));
                }
            }
            return result;
        }
        if (value instanceof Map<?, ?>) {
            return List.of(asMap(value));
        }
        return List.of();
    }
}
