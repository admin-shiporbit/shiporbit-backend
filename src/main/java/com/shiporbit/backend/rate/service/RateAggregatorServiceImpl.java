package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.exception.DeliveryRequestException;
import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.dto.response.RateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RateAggregatorServiceImpl implements RateAggregatorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateAggregatorServiceImpl.class);

    private final Map<String, DeliveryPartnerClient> clientsByCode;

    public RateAggregatorServiceImpl(List<DeliveryPartnerClient> clients) {
        this.clientsByCode = clients.stream()
                .map(MarkupPartnerClient::new)
                .collect(Collectors.toMap(DeliveryPartnerClient::partnerCode, Function.identity()));
    }

    @Override
    public RateResponse getRate(String partnerCode, RequestParamRecord request) {
        DeliveryPartnerClient client = clientsByCode.get(partnerCode);
        if (client == null) {
            LOGGER.warn("Rate requested for unknown partner code {}", partnerCode);
            throw new IllegalArgumentException("Unknown partner " + partnerCode);
        }

        if (!client.isServiceable(request)) {
            LOGGER.warn("Partner {} is not serviceable for the request {}", partnerCode, request);
            throw new IllegalStateException("Service is not available for the route");
        }

        return client.getRate(request);
    }

    @Override
    public Map<String, RateResponse> compareRates(RequestParamRecord request) {
        LOGGER.debug("Comparing rates across {} partner(s): {}", clientsByCode.size(), clientsByCode.keySet());
        Map<String, RateResponse> rateResponses = new LinkedHashMap<>();
        for (DeliveryPartnerClient client : clientsByCode.values()) {
            try {
                if (client.isServiceable(request)) {
                    RateResponse rateResponse = client.getRate(request);
                    rateResponses.put(client.partnerCode(), rateResponse);
                    LOGGER.debug("Partner {} returned a quote: finalFreight={}", client.partnerCode(), rateResponse.finalFreight());
                } else {
                    LOGGER.debug("Partner {} is not serviceable for the request", client.partnerCode());
                }
            } catch (Exception e) {
                LOGGER.warn("Partner {} is not available for the request {}", client.partnerCode(), request, e);
            }
        }
        LOGGER.debug("Rate comparison done, {}/{} partner(s) returned a quote", rateResponses.size(), clientsByCode.size());
        return rateResponses;
    }


    // International requests (partnerOptions.destinationCountry other than IN, used by
    // UPS) carry a foreign postal code, so the 6-digit Indian PIN rule doesn't apply.
    private boolean isInternational(RequestParamRecord request) {
        Object country = request.partnerOptions().get("destinationCountry");
        return country != null && !country.toString().isBlank()
                && !"IN".equalsIgnoreCase(country.toString().trim());
    }

    @Override
    public void validateRequest(RequestParamRecord request) {
        if(request.sourcePinCode().length() != 6) {
            throw new DeliveryRequestException("Source pin code should be only 6 digits",new Exception());
        } else if(!isInternational(request) && request.destinationPinCode().length() != 6) {
            throw new DeliveryRequestException("Destination pin code should be only 6 digits",new Exception());
        } else if(request.weight()<=0.0){
            throw new DeliveryRequestException("Weight should be greater than 0",new Exception());
        } else if(request.dimension().getBoxCount()<=0){
            throw new DeliveryRequestException("Dimension box count should be greater than 0",new Exception());
        } else if(request.dimension().getWidth()<=0.0){
            throw new DeliveryRequestException("Dimension width should be greater than 0",new Exception());
        } else if(request.dimension().getHeight()<=0.0){
            throw new DeliveryRequestException("Dimension height should be greater than 0",new Exception());
        } else if (request.dimension().getLength()<0.0){
            throw new DeliveryRequestException("Dimension length should be greater than 0",new Exception());
        }
    }
}
