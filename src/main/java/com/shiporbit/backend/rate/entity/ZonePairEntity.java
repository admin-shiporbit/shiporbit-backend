package com.shiporbit.backend.rate.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One SO_ZONE_PAIR row: zone for (origin value, destination value) of a group, used by PAIR rules. */
@Entity
@Table(name = "SO_ZONE_PAIR")
@Getter
@Setter
@NoArgsConstructor
public class ZonePairEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", updatable = false, nullable = false)
    private Long id;

    @Column(name = "PARTNER_ID", nullable = false)
    private Integer partnerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "PRICING_MODEL", nullable = false, length = 20)
    private PricingModel pricingModel;

    @Column(name = "GROUP_NAME", nullable = false, length = 30)
    private String groupName;

    @Column(name = "ORIGIN_VALUE", nullable = false, length = 20)
    private String originValue;

    @Column(name = "DESTINATION_VALUE", nullable = false, length = 20)
    private String destinationValue;

    @Column(name = "LOCATION_KEY", nullable = false, length = 20)
    private String locationKey;             // must match SO_RATE_SLAB.LOCATION_KEY
}
