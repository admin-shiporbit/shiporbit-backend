package com.shiporbit.backend.rate.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "SO_PARTNER_PINCODE")
@Getter
@Setter
@NoArgsConstructor
public class PartnerPincodeEntity {

    @EmbeddedId
    private PartnerPincodeId id;

    @Column(name = "CITY_CODE", nullable = false, length = 30)
    private String cityCode;          // hyperlocal same-city check

    @Column(name = "STATE_ID", nullable = false)
    private Integer stateId;

    @Column(name = "PICKUP", nullable = false)
    private boolean pickup;

    @Column(name = "DELIVERY", nullable = false)
    private boolean delivery;

    @Column(name = "COD", nullable = false)
    private boolean cod;

    @Column(name = "PREPAID", nullable = false)
    private boolean prepaid;

    @Column(name = "REVERSE_PICKUP", nullable = false)
    private boolean reversePickup;

    @Column(name = "ODA", nullable = false)
    private boolean oda;

    @Column(name = "SPECIAL_CITY", nullable = false)
    private boolean specialCity;
}