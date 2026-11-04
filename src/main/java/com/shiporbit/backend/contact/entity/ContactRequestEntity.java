package com.shiporbit.backend.contact.entity;

import com.shiporbit.backend.contact.dto.ContactRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** One website contact-form submission (V15__contact_request.sql). */
@Entity
@Table(name = "so_contact_request")
@Getter
@Setter
@NoArgsConstructor
public class ContactRequestEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 150)
    private String company;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String topic;

    @Column(length = 50)
    private String volume;

    @Column(nullable = false, length = 5000)
    private String message;

    @Column(length = 200)
    private String website;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContactRequestStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ContactEmailStatus userEmailStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ContactEmailStatus teamEmailStatus;

    @Column(length = 500)
    private String error;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public static ContactRequestEntity from(ContactRequest r) {
        ContactRequestEntity e = new ContactRequestEntity();
        e.id = UUID.randomUUID();
        e.name = r.name();
        e.email = r.email();
        e.company = r.company();
        e.phone = r.phone();
        e.topic = r.topic();
        e.volume = r.volume();
        e.message = r.message();
        e.website = r.website();
        e.status = ContactRequestStatus.RECEIVED;
        return e;
    }
}
