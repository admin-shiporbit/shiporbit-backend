package com.shiporbit.backend.rate.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "SO_RATE_SLAB")
@Getter
@Setter
@NoArgsConstructor
public class RateSlabEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", updatable = false, nullable = false)
    private Long id;

    @Column(name = "RATE_CARD_ID", nullable = false)
    private Long rateCardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "DIRECTION", nullable = false, length = 10)
    private SlabDirection direction;

    @Column(name = "LOCATION_KEY", nullable = false, length = 20)
    private String locationKey;                    // zone / band / 'N1-S1' / 'BIKE'

    @Enumerated(EnumType.STRING)
    @Column(name = "UNIT", nullable = false, length = 5)
    private RateUnit unit;

    @Column(name = "FROM_UNIT", nullable = false, precision = 10, scale = 3)
    private BigDecimal fromUnit;                   // exclusive

    @Column(name = "TO_UNIT", precision = 10, scale = 3)
    private BigDecimal toUnit;                     // inclusive; null = no upper limit

    @Column(name = "FIRST_UNIT", nullable = false, precision = 10, scale = 3)
    private BigDecimal firstUnit;

    @Column(name = "FIRST_RATE", nullable = false, precision = 10, scale = 2)
    private BigDecimal firstRate;

    @Column(name = "ADD_UNIT", nullable = false, precision = 10, scale = 3)
    private BigDecimal addUnit;

    @Column(name = "ADD_RATE", nullable = false, precision = 10, scale = 2)
    private BigDecimal addRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "PAYMENT_MODE", length = 10)
    private PaymentMode paymentMode;               // null = any

    @Enumerated(EnumType.STRING)
    @Column(name = "SHIPMENT_TYPE", length = 10)
    private ShipmentType shipmentType;             // null = any

    /** FROM_UNIT < x <= TO_UNIT */
    public boolean covers(BigDecimal x) {
        return x.compareTo(fromUnit) > 0 && (toUnit == null || x.compareTo(toUnit) <= 0);
    }
}