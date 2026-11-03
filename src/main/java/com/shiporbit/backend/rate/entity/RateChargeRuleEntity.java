package com.shiporbit.backend.rate.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "SO_RATE_CHARGE_RULE")
@Getter
@Setter
@NoArgsConstructor
public class RateChargeRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", updatable = false, nullable = false)
    private Long id;

    @Column(name = "RATE_CARD_ID", nullable = false)
    private Long rateCardId;

    @Column(name = "CHARGE_CODE", nullable = false, length = 30)
    private String chargeCode;                      // breakdown line code, e.g. FUEL_SURCHARGE

    @Column(name = "SEQ", nullable = false)
    private Integer seq;                            // apply order

    @Enumerated(EnumType.STRING)
    @Column(name = "CALC_TYPE", nullable = false, length = 10)
    private CalcType calcType;

    @Column(name = "RATE_VALUE", nullable = false, precision = 12, scale = 4)
    private BigDecimal rateValue = BigDecimal.ZERO; // % for PERCENT, per-unit for PER_UNIT

    @Column(name = "FIXED_AMOUNT", nullable = false, precision = 10, scale = 2)
    private BigDecimal fixedAmount = BigDecimal.ZERO;

    @Column(name = "MIN_AMOUNT", precision = 10, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "MAX_AMOUNT", precision = 10, scale = 2)
    private BigDecimal maxAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "BASE", nullable = false, length = 20)
    private ChargeBase base;

    @Convert(converter = CsvStringListConverter.class)
    @Column(name = "BASE_CODES", length = 200)
    private List<String> baseCodes = List.of();     // for BASE = CHARGE_CODES, e.g. [BASE, ODA]

    @Column(name = "TIER_FROM", precision = 12, scale = 2)
    private BigDecimal tierFrom;                    // inclusive

    @Column(name = "TIER_TO", precision = 12, scale = 2)
    private BigDecimal tierTo;                      // exclusive; null = no upper limit

    @Enumerated(EnumType.STRING)
    @Column(name = "APPLIES_WHEN", nullable = false, length = 20)
    private AppliesWhen appliesWhen;

    @Column(name = "APPLIES_VALUE", length = 50)
    private String appliesValue;                    // for CATEGORY, e.g. ELECTRONICS

    @Convert(converter = DirectionSetConverter.class)
    @Column(name = "DIRECTIONS", nullable = false, length = 30)
    private Set<ShipmentDirection> directions = EnumSet.allOf(ShipmentDirection.class);

    @Column(name = "VALID_FROM")
    private LocalDate validFrom;                    // null = open

    @Column(name = "VALID_TO")
    private LocalDate validTo;                      // null = open

    public boolean validOn(LocalDate date) {
        return (validFrom == null || !date.isBefore(validFrom))
                && (validTo == null || !date.isAfter(validTo));
    }

    public boolean appliesTo(ShipmentDirection direction) {
        return directions.contains(direction);
    }

    /** TIER_FROM <= value < TIER_TO; no tier = always in. */
    public boolean inTier(BigDecimal value) {
        return (tierFrom == null || value.compareTo(tierFrom) >= 0)
                && (tierTo == null || value.compareTo(tierTo) < 0);
    }
}