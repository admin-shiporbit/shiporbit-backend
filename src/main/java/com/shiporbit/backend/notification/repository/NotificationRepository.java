package com.shiporbit.backend.notification.repository;

import com.shiporbit.backend.notification.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<NotificationLog, Long> {
    boolean existsByIdempotencyKey(String key);

    Optional<NotificationLog> findByIdempotencyKey(String key);

    Optional<NotificationLog> findByProviderMsgId(String providerMsgId);
}
