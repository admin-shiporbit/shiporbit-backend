package com.shiporbit.backend.rate.entity;

public enum AppliesWhen {
    ALWAYS, COD, PREPAID, ODA_DESTINATION, ODA_PICKUP, SPECIAL_CITY,
    RISK_OWNER, RISK_CARRIER, ADDON_OTP, ADDON_QC, ADDON_FRAGILE, CATEGORY,
    /** Destination pincode's STATE_ID equals APPLIES_VALUE (e.g. green tax for delivery within Delhi). */
    DEST_STATE,
    /** Any package's dead weight per piece is greater than APPLIES_VALUE kg (odd-size / heavy handling). */
    PIECE_WEIGHT_OVER
}
