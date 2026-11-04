package com.shiporbit.backend.rate.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "SO_ZONE_MAP")
@Getter
@Setter
@NoArgsConstructor
public class ZoneMapEntity {

    @EmbeddedId
    private ZoneMapId id;

    @Column(name = "LOCATION_KEY", nullable = false, length = 20)
    private String locationKey;             // must match SO_RATE_SLAB.LOCATION_KEY
}