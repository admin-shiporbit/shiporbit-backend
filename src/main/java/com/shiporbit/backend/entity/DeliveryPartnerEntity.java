package com.shiporbit.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "SO_DELIVERY_PARTNER_LIST")
@Getter
@Setter
public class DeliveryPartnerEntity {
    // V1: "id INT PRIMARY KEY" with no sequence; ids are assigned in the seed migrations (1-14).
    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "partner_name", nullable = false, unique = true, length = 50)
    private String partnerName;

}
