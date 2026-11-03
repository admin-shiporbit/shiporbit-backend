package com.shiporbit.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "SO_PICKUP_ADDRESS")
@Getter
@Setter
@NoArgsConstructor
public class PickupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "LABEL", nullable = false, length = 48)
    private String label;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "USER_ID",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "FK_PICKUP_USER_ID")
    )
    private Users user;

    @Column(name = "ISD_CODE", nullable = false, length = 3)
    private String isdCode;

    @Column(name = "PHONE", nullable = false, length = 10)
    private String phoneNumber;

    @Column(name = "ADDRESS_LINE1", length = 256)
    private String addressLine1;

    @Column(name = "ADDRESS_LINE2", length = 256)
    private String addressLine2;

    @Column(name = "CITY", nullable = false, length = 200)
    private String city;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "STATE_ID",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_STATE_ID")
    )
    private StateEntity state;

    @Column(name = "PIN_CODE", nullable = false, length = 6)
    private String pinCode;

    @Column(name = "IS_DEFAULT", nullable = false)
    private boolean isDefault;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
