package com.shiporbit.backend.contact.entity;

/** Result of one of the two emails (user confirmation / team notification). */
public enum ContactEmailStatus {
    SENT,
    FAILED,
    /** Not attempted: email disabled, or (team email) no team address configured. */
    SKIPPED
}
