package com.shiporbit.backend.dto;

public record PincodeResponse(String id,
                              String state,
                              String stateCode,
                              String district,
                              String deliveryPartner,
                              boolean isPrepaidAvailable,
                              boolean isReversePickAvailable,
                              boolean isRSPL_Available,
                              boolean isCOD_Available,
                              boolean isCashAvailable) {
}
