package com.shiporbit.backend.contact.repository;

import com.shiporbit.backend.contact.entity.ContactRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ContactRequestRepository extends JpaRepository<ContactRequestEntity, UUID> {
}
