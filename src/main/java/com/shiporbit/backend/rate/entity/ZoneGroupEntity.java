package com.shiporbit.backend.rate.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One SO_ZONE_GROUP row: a city or a state and its value in a named group (e.g. REGION: UP -> NORTH). */
@Entity
@Table(name = "SO_ZONE_GROUP")
@Getter
@Setter
@NoArgsConstructor
public class ZoneGroupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", updatable = false, nullable = false)
    private Long id;

    @Column(name = "PARTNER_ID", nullable = false)
    private Integer partnerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "PRICING_MODEL", nullable = false, length = 20)
    private PricingModel pricingModel;      // never HYPERLOCAL (DB check)

    @Column(name = "GROUP_NAME", nullable = false, length = 30)
    private String groupName;

    @Column(name = "STATE_ID")
    private Integer stateId;                // exactly one of stateId / cityCode is set (DB check)

    @Column(name = "CITY_CODE", length = 30)
    private String cityCode;                // upper case, compared with SO_PARTNER_PINCODE.CITY_CODE

    @Column(name = "GROUP_VALUE", nullable = false, length = 20)
    private String groupValue;
}
