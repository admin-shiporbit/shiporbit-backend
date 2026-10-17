package com.shiporbit.backend.rate.engine.zone;

import com.shiporbit.backend.rate.entity.ZoneCondition;

/**
 * One SO_ZONE_RULE row. Rules of a partner + pricing model run in SEQ order; the first match wins.
 * groupName: null for ANY / SAME_STATE. locationKey: null for PAIR (the key comes from SO_ZONE_PAIR).
 */
public record ZoneRule(int seq, ZoneCondition condition, String groupName, String locationKey) { }
