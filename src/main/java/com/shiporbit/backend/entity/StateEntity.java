package com.shiporbit.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "SO_STATES")
@Getter
@Setter
@NoArgsConstructor
public class StateEntity {

    @Id
    @Column(name = "STATE_ID", nullable = false)
    private Integer id;

    @Column(name = "STATE_NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "STATE_CODE", nullable = false, unique = true, length = 10)
    private String code;

    @Column(name = "REGION_TYPE", nullable = false, length = 20)
    private String regionType;

    @Column(name = "COUNTRY", length = 50)
    private String country;
}
