package com.shiporbit.backend.rate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PartnerPincodeId implements Serializable {

    @Column(name = "PARTNER_ID", nullable = false)
    private Integer partnerId;

    @Column(name = "PINCODE", nullable = false, length = 6)
    private String pincode;
}