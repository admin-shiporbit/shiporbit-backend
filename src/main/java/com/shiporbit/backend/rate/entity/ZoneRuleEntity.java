package com.shiporbit.backend.rate.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One SO_ZONE_RULE row. Rules of a partner + pricing model run in SEQ order; the first match wins. */
@Entity
@Table(name = "SO_ZONE_RULE")
@Getter
@Setter
@NoArgsConstructor
public class ZoneRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", updatable = false, nullable = false)
    private Long id;

    @Column(name = "PARTNER_ID", nullable = false)
    private Integer partnerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "PRICING_MODEL", nullable = false, length = 20)
    private PricingModel pricingModel;

    @Column(name = "SEQ", nullable = false)
    private Integer seq;

    @Enumerated(EnumType.STRING)
    @Column(name = "MATCH_CONDITION", nullable = false, length = 15)
    private ZoneCondition matchCondition;

    @Column(name = "GROUP_NAME", length = 30)
    private String groupName;               // null for ANY / SAME_STATE

    @Column(name = "LOCATION_KEY", length = 20)
    private String locationKey;             // null for PAIR; else must match SO_RATE_SLAB.LOCATION_KEY
}
