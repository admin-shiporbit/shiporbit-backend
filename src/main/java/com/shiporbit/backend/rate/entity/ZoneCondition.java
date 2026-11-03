package com.shiporbit.backend.rate.entity;

/**
 * SO_ZONE_RULE.MATCH_CONDITION: how one zone rule tests an origin -> destination lane.
 * "group" is SO_ZONE_RULE.GROUP_NAME, a named grouping of pincodes from SO_ZONE_GROUP
 * (e.g. REGION, METRO, REMOTE). A pincode is "in" a group when its CITY_CODE or STATE_ID is listed there.
 * Evaluated in PartnerZoneRules (same idea as ChargeEngine switching on AppliesWhen).
 */
public enum ZoneCondition {
    /** Always matches. Use it as the last rule (fallback zone). */
    ANY,
    /** Origin and destination are in the same state (SO_PARTNER_PINCODE.STATE_ID). */
    SAME_STATE,
    /** Both pincodes are in the group with the same value, e.g. both REGION = NORTH. */
    SAME_GROUP,
    /** Both pincodes are in the group (any value), e.g. metro to metro. */
    BOTH_IN,
    /** The origin pincode is in the group. */
    ORIGIN_IN,
    /** The destination pincode is in the group. */
    DEST_IN,
    /** Origin or destination is in the group. */
    EITHER_IN,
    /** Zone comes from SO_ZONE_PAIR for (origin's group value, destination's group value). */
    PAIR
}
