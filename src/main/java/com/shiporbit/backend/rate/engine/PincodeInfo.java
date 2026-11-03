package com.shiporbit.backend.rate.engine;

/**
 * Serviceability flags of one partner at one pincode (SO_PARTNER_PINCODE).
 * cityCode and stateId also feed zone rules (SO_ZONE_GROUP matches on CITY_CODE or STATE_ID).
 */
public record PincodeInfo(String pincode, String cityCode, Integer stateId, boolean pickup, boolean delivery,
                          boolean cod, boolean prepaid, boolean reversePickup, boolean oda,
                          boolean specialCity) { }
