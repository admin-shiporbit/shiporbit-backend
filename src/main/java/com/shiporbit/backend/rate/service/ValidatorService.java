package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.exception.DeliveryRequestException;
import com.shiporbit.backend.rate.dto.request.RequestParamRecord;

public class ValidatorService {

    public void validateaRequest(RequestParamRecord request) {
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
