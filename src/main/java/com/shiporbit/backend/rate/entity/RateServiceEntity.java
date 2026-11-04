package com.shiporbit.backend.rate.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "SO_RATE_SERVICE")
@Getter
@Setter
@NoArgsConstructor
public class RateServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", updatable = false, nullable = false)
    private Long id;

    @Column(name = "PARTNER_ID", nullable = false)
    private Integer partnerId;

    @Column(name = "SERVICE_CODE", nullable = false, length = 50)
    private String serviceCode;

    @Column(name = "SERVICE_NAME", nullable = false, length = 100)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "PRICING_MODEL", nullable = false, length = 20)
    private PricingModel pricingModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "MODE", nullable = false, length = 20)
    private ServiceMode mode;

    @Column(name = "ENABLED", nullable = false)
    private boolean enabled = true;

    @Column(name = "CREATED_AT", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;   // DB default
}
