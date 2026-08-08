package com.shiporbit.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "SO_USERS", uniqueConstraints = @UniqueConstraint(
        name = "SO_UQ_USERS_EMAIL",
        columnNames = "EMAIL"
))
@Getter
@Setter
@NoArgsConstructor
public class Users {


    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID")
    private UUID id;

    @Column(name = "EMAIL", length = 140)
    @NotBlank
    @Email
    private String email;

    @Column(name = "PASSWORD_HASH", length = 256, nullable = false)
    private String passwordHash;

    @Column(name = "FULL_NAME", nullable = false, length = 120)
    private String fullName;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ROLE_ID",
            nullable = false,
            foreignKey = @ForeignKey(name = "SO_FK_USERS_ROLE_ID")
    )
    private Role role;

    @Column(name = "ENABLED", nullable = false)
    private boolean isEnabled;

    @Column(name = "CREATED_AT", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersisit() {
        LocalDateTime now  = LocalDateTime.now();

        if(createdAt == null){
            createdAt = now;
        }

        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
