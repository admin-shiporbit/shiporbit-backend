package com.shiporbit.backend.contact.entity;

/** Overall state of a contact request (SO_CONTACT_REQUEST.STATUS). */
public enum ContactRequestStatus {
    /** Saved; emails not processed yet. */
    RECEIVED,
    /** Both emails sent (or the team email skipped because no team address is configured). */
    COMPLETED,
    /** One email sent, the other failed. */
    PARTIAL,
    /** No email could be sent (both failed, or email is disabled). The request itself is still saved. */
    FAILED
}
