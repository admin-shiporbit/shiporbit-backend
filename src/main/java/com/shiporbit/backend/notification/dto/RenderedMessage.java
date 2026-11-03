package com.shiporbit.backend.notification.dto;

/**
 * @param subject       email subject; null for SMS/WhatsApp
 * @param dltTemplateId DLT template id for SMS; null for other channels
 */
public record RenderedMessage(String subject, String body, String dltTemplateId) {
}
