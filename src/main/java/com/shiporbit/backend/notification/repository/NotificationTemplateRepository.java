package com.shiporbit.backend.notification.repository;

import com.shiporbit.backend.notification.constants.Channel;
import com.shiporbit.backend.notification.constants.NotificationPurpose;
import com.shiporbit.backend.notification.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {
    Optional<NotificationTemplate> findByPurposeAndChannelAndActiveTrue(NotificationPurpose purpose, Channel channel);
}
