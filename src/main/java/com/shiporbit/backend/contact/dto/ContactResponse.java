package com.shiporbit.backend.contact.dto;

import java.util.UUID;

/**
 * Returned as soon as the request is saved (HTTP 202). Emails are sent afterwards on a background thread,
 * so this does not say whether they were delivered. referenceId: quote it to trace the request
 * (SO_CONTACT_REQUEST.ID, also used in the email idempotency keys).
 */
public record ContactResponse(UUID referenceId, String status, String message) {

    public static ContactResponse received(UUID id) {
        return new ContactResponse(id, "RECEIVED",
                "Thanks for contacting ShipOrbit. We have received your request and will get back to you shortly.");
    }
}
