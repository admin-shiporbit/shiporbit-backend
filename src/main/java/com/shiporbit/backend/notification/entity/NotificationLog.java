package com.shiporbit.backend.notification.entity;

import com.shiporbit.backend.notification.constants.Channel;
import com.shiporbit.backend.notification.constants.NotificationPurpose;
import com.shiporbit.backend.notification.constants.NotificationStatus;
import com.shiporbit.backend.notification.dto.NotificationRequest;
import com.shiporbit.backend.notification.dto.NotificationResponse;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "so_notification_log")
@Getter
@Setter
@NoArgsConstructor
public class NotificationLog {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationPurpose purpose;

    @Column(nullable = false, length = 150)
    private String recipient;

    @Column(length = 30)
    private String provider;

    @Column(length = 100)
    private String providerMsgId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(length = MAX_ERROR_LENGTH)
    private String error;

    private int attempts;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public static NotificationLog from(NotificationRequest request, Channel channel, NotificationResponse response) {
        NotificationLog log = new NotificationLog();
        log.idempotencyKey = request.idempotencyKey();
        log.userId = request.userId();
        log.channel = channel;
        log.purpose = request.purpose();
        log.recipient = request.recipient();
        log.provider = response.provider();
        log.providerMsgId = response.providerMessageId();
        log.status = response.status();
        log.error = response.error() == null
                ? null
                : response.error().substring(0, Math.min(MAX_ERROR_LENGTH, response.error().length()));
        log.attempts = 1;
        return log;
    }
}
