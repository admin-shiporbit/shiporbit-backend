package com.shiporbit.backend.notification.controller;

import com.shiporbit.backend.notification.dto.BulkEmailResponse;
import com.shiporbit.backend.notification.dto.NotificationResponse;
import com.shiporbit.backend.notification.dto.TestBulkEmailRequest;
import com.shiporbit.backend.notification.dto.TestEmailRequest;
import com.shiporbit.backend.notification.dto.TestTemplateEmailRequest;
import com.shiporbit.backend.notification.service.NotificationTestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only endpoints (see SecurityConfig) to verify email delivery. */
@RestController
@RequestMapping("/api/v1/notifications/test")
public class NotificationTestController {

    private final NotificationTestService testService;

    public NotificationTestController(NotificationTestService testService) {
        this.testService = testService;
    }

    /** Sends a raw email straight through the configured EmailProvider. */
    @PostMapping("/email")
    public ResponseEntity<NotificationResponse> sendEmail(@Valid @RequestBody TestEmailRequest request) {
        return toResponse(testService.sendRaw(request));
    }

    /** Sends a templated email through EmailChannel; also writes to so_notification_log. */
    @PostMapping("/email/template")
    public ResponseEntity<NotificationResponse> sendTemplateEmail(
            @Valid @RequestBody TestTemplateEmailRequest request) {
        return toResponse(testService.sendTemplate(request));
    }

    /** Sends one separate templated email per recipient; returns per-recipient results. */
    @PostMapping("/email/template/bulk")
    public ResponseEntity<BulkEmailResponse> sendTemplateEmailToAll(
            @Valid @RequestBody TestBulkEmailRequest request) {
        BulkEmailResponse response = testService.sendTemplateToAll(request);
        HttpStatus status = response.sent() > 0 ? HttpStatus.OK : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(response);
    }

    private ResponseEntity<NotificationResponse> toResponse(NotificationResponse response) {
        HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(response);
    }
}
