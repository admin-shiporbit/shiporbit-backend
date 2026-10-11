package com.shiporbit.backend.notification.constants;

import lombok.Getter;

@Getter
public enum Channel {
    SMS(1, "SMS"),
    EMAIL(2, "Email"),
    WHATSAPP(3, "Whatsapp"),
    PUSH(4, "Push");

    private final int id;
    private final String label;

    Channel(int id, String label) {
        this.id = id;
        this.label = label;
    }
}
