package com.shiporbit.backend.rate.serviceability;

import java.util.List;

public record ServiceabilityCheckResponse(String originPincode, String destinationPincode, List<Result> results) {

    /** api: B2C / LTL (Delhivery), ECOMM / HYPERLOCAL (Shree Maruti). */
    /** errorCode: null when SERVICEABLE, else ErrorCode.code() (SO-SVC-* / SO-PTR-*). */
    public record Result(String partner, String api, Serviceability.Status status, String errorCode, String reason) {
    }
}
