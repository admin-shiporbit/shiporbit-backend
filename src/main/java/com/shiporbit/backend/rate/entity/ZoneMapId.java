package com.shiporbit.backend.rate.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ZoneMapId implements Serializable {

    @Column(name = "PARTNER_ID", nullable = false)
    private Integer partnerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "PRICING_MODEL", nullable = false, length = 20)
    private PricingModel pricingModel;      // never HYPERLOCAL (DB check)

    @Column(name = "ORIGIN_PINCODE", nullable = false, length = 6)
    private String originPincode;

    @Column(name = "DESTINATION_PINCODE", nullable = false, length = 6)
    private String destinationPincode;
}