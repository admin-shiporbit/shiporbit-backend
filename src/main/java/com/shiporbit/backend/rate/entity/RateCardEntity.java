package com.shiporbit.backend.rate.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "SO_RATE_CARD")
@Getter
@Setter
@NoArgsConstructor
public class RateCardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", updatable = false, nullable = false)
    private Long id;

    @Column(name = "SERVICE_ID", nullable = false)
    private Long serviceId;

    @Column(name = "VERSION_LABEL", nullable = false, length = 40)
    private String versionLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private CardStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "DATA_STATUS", nullable = false, length = 10)
    private DataStatus dataStatus;

    @Column(name = "EFFECTIVE_FROM", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "EFFECTIVE_TO")
    private LocalDate effectiveTo;                    // null = open-ended

    @Column(name = "VOLUMETRIC_DIVISOR", precision = 10, scale = 2)
    private BigDecimal volumetricDivisor;             // null = dead weight only

    @Column(name = "CFT_KG", precision = 10, scale = 2)
    private BigDecimal cftKg;                         // B2B only

    @Column(name = "MIN_CHARGEABLE_WEIGHT_KG", nullable = false, precision = 10, scale = 3)
    private BigDecimal minChargeableWeightKg = BigDecimal.ZERO;

    @Column(name = "MAX_WEIGHT_KG", nullable = false, precision = 10, scale = 3)
    private BigDecimal maxWeightKg;

    @Column(name = "MAX_DISTANCE_KM", precision = 10, scale = 2)
    private BigDecimal maxDistanceKm;                 // HYPERLOCAL only

    @Enumerated(EnumType.STRING)
    @Column(name = "MPS_RULE", nullable = false, length = 10)
    private MpsRule mpsRule = MpsRule.TOTAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "RTO_RULE", nullable = false, length = 25)
    private RtoRule rtoRule = RtoRule.PERCENT_OF_FORWARD;

    @Column(name = "RTO_PERCENT", precision = 6, scale = 2)
    private BigDecimal rtoPercent;

    @Column(name = "MIN_BILL", nullable = false, precision = 10, scale = 2)
    private BigDecimal minBill = BigDecimal.ZERO;

    @Column(name = "RATES_INCLUDE_GST", nullable = false)
    private boolean ratesIncludeGst;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROUNDING", nullable = false, length = 10)
    private Rounding rounding = Rounding.CEIL_STEP;

    @Column(name = "CREATED_AT", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;                  // DB default

    @UpdateTimestamp
    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;
}