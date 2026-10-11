package com.shiporbit.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "SO_DELIVEY_PIN_CODE_LIST")
public class PincodeEntity {
    // V1: "id BIGSERIAL" -> IDENTITY (SEQUENCE looked for a sequence that does not exist).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    // V1 column is PinCode (unquoted) = "pincode" in PostgreSQL. name="pinCode" was turned into
    // "pin_code" by Spring's camelCase -> snake_case naming strategy, which does not exist.
    @Column(name = "pincode", nullable = false, length = 6)
    private String pinCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FK_state_id", nullable = false)
    private StateEntity stateEntity;

}
