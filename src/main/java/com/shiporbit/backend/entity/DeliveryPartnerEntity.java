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
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    private Long id;

    @Column(name = "partnerName", nullable = false, unique = true, length = 50)
    private String partnerName;

}
