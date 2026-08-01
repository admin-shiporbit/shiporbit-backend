package com.shiporbit.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Entity
@Table(name = "SO_ROLE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @Id
    @Column(name = "ID")
    private int id;

    @Column(name = "role", nullable = false, length = 15)
    @NotNull
    private String role;
}
