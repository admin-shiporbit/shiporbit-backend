package com.shiporbit.backend.rate.engine;

/** Serviceability flags of one partner at one pincode (SO_PARTNER_PINCODE). */
public record PincodeInfo(String pincode, String cityCode, boolean pickup, boolean delivery,
                          boolean cod, boolean prepaid, boolean reversePickup, boolean oda,
                          boolean specialCity) { }
