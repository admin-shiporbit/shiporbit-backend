package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.error.ErrorCode;
import com.shiporbit.backend.exception.PartnerApiException;
import com.shiporbit.backend.logging.Flow;
import com.shiporbit.backend.rate.auth.CachingTokenProvider;
import com.shiporbit.backend.rate.auth.EkartTokenFetcher;
import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.dto.response.RateResponse;
import com.shiporbit.backend.rate.routing.EkartConfigProperties;
import com.shiporbit.backend.rate.serviceability.Serviceability;
import com.shiporbit.backend.rate.serviceability.ServiceabilityCache;
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
import java.util.Map;

/**
 * Ekart Elite live client (partner 10, registered in LivePartnerQuoteProvider as "ekart").
 * <ol>
 *   <li>Serviceability: GET /api/v2/serviceability/{pincode} for origin and destination (cached 12 h per pincode);
 *       origin needs forward pickup, destination forward drop (+ COD within max_cod_amount for COD).</li>
 *   <li>Rate: POST /data/pricing/estimate (Bearer token from EkartTokenFetcher, cached ~24 h).</li>
 * </ol>
 * Replaces the EKART-2026-10 rate card (retired in V17).
 */
@Component
public class EkartPartnerClient implements DeliveryPartnerClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(EkartPartnerClient.class);

    private final RestClient ekartClient;
    private final EkartConfigProperties props;
    private final CachingTokenProvider tokenProvider;
    private final ServiceabilityCache serviceabilityCache;

    public EkartPartnerClient(RestClient ekartClient, EkartConfigProperties props, EkartTokenFetcher tokenFetcher,
                              ServiceabilityCache serviceabilityCache) {
        this.ekartClient = ekartClient;
        this.props = props;
        this.tokenProvider = new CachingTokenProvider(tokenFetcher);
        this.serviceabilityCache = serviceabilityCache;
    }

    @Override
    public String partnerCode() {
        return "ekart";
    }

    @Override
    public String partnerName() {
        return "Ekart";
    }

    @Override
    public boolean isServiceable(RequestParamRecord request) {
        return checkServiceability(request).status() != Serviceability.Status.NOT_SERVICEABLE;
    }

    @Override
    public Serviceability checkServiceability(RequestParamRecord request) {
        if (!props.configured()) {
            return Serviceability.unknown(ErrorCode.PARTNER_NOT_CONFIGURED,
                    "Ekart credentials not set (EKART_USERNAME / EKART_PASSWORD / EKART_CLIENT_ID)");
        }
        Flow.step(LOGGER, "Ekart: serviceability for origin and destination");
        Map<String, Object> o = request.partnerOptions();
        boolean cod = Boolean.TRUE.equals(o.get("cod"));
        BigDecimal codAmount = o.get("codAmount") instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO;
        Serviceability origin = EkartResponseParser.evaluate(pin(request.sourcePinCode()), request.sourcePinCode(), true, cod, codAmount);
        Serviceability destination = EkartResponseParser.evaluate(pin(request.destinationPinCode()), request.destinationPinCode(), false, cod, codAmount);
        return Serviceability.both(origin, destination);
    }

    /** One pincode, cached 12 h (definite answers only). */
    private Serviceability pin(String pin) {
        return serviceabilityCache.get("ekart:pin:" + pin, () -> {
            Object response;
            try {
                String token = tokenProvider.getValidToken().get("token");
                response = ekartClient.get()
                        .uri(props.serviceabilityEndpoint(), pin)
                        .accept(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .body(Object.class);
            } catch (HttpStatusCodeException e) {
                LOGGER.warn("Ekart serviceability {} for {}: {}", e.getStatusCode(), pin, e.getResponseBodyAsString());
                return Serviceability.unknown(Serviceability.codeForHttp(e.getStatusCode().value()),
                        "Ekart serviceability HTTP " + e.getStatusCode().value());
            } catch (RuntimeException e) {
                LOGGER.warn("Ekart serviceability failed for {}: {}", pin, e.getMessage());
                return Serviceability.unknown(e instanceof PartnerApiException pae ? pae.errorCode() : PartnerApiException.codeForCause(e),
                        "Ekart serviceability failed: " + e.getMessage());
            }
            LOGGER.debug("Ekart serviceability response for {}: {}", pin, response);
            return EkartResponseParser.pin(response, pin);
        });
    }

    @Override
    public RateResponse getRate(RequestParamRecord request) {
        if (!props.configured()) {
            throw new PartnerApiException(ErrorCode.PARTNER_NOT_CONFIGURED, "Ekart credentials not set", null,
                    HttpStatus.SERVICE_UNAVAILABLE.value());
        }
        Flow.step(LOGGER, "Ekart: estimate API {} -> {}", request.sourcePinCode(), request.destinationPinCode());
        String token = tokenProvider.getValidToken().get("token");
        Map<String, Object> body = EkartResponseParser.estimateBody(request, props.billingClientType(), props.shippingDirection());
        LOGGER.debug("Outgoing Ekart estimate request:\n{}", CurlLogger.toCurl("POST", props.routerUrl() + props.rateEndpoint(),
                Map.of("Content-Type", "application/json", "Authorization", "Bearer " + token), body));

        Object response;
        try {
            response = ekartClient.post()
                    .uri(props.rateEndpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(body)
                    .retrieve()
                    .body(Object.class);
        } catch (HttpStatusCodeException e) {
            throw PartnerApiException.from(e, "Ekart");
        } catch (RestClientException e) {
            throw new PartnerApiException("Unable to connect to Ekart estimate API", e, HttpStatus.BAD_GATEWAY.value());
        }
        LOGGER.debug("Ekart estimate response: {}", response);
        RateResponse rate = EkartResponseParser.estimate(response, partnerCode(), partnerName());
        LOGGER.debug("Ekart rate fetched: finalFreight={}", rate.finalFreight());
        return rate;
    }
}
