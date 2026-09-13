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
            throw new IllegalArgumentException("Unknown partner " + partnerCode);
        }

        if (!client.isServiceable(request)) {
            throw new IllegalStateException("Service is not available for the route");
        }

        return client.getRate(request);
    }

    @Override
    public Map<String, RateResponse> compareRates(RequestParamRecord request) {
        Map<String, RateResponse> rateResponses = new LinkedHashMap<>();
        for (DeliveryPartnerClient client : clientsByCode.values()) {
            try {
                if (client.isServiceable(request)) {

                    rateResponses.put(client.partnerCode(), client.getRate(request));
                }
            } catch (Exception e) {
                LOGGER.warn("Partner {} is not available for the request {}", client.partnerCode(), request, e);
            }
        }
        return rateResponses;
    }


    @Override
    public void validateRequest(RequestParamRecord request) {
        if(request.sourcePinCode().length() != 6) {
            throw new DeliveryRequestException("Source pin code should be only 6 digits",new Exception());
        } else if(request.destinationPinCode().length() != 6) {
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
