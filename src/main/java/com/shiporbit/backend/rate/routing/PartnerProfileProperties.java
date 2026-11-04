package com.shiporbit.backend.rate.routing;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Display data per partner (SO_DELIVERY_PARTNER_LIST.ID -> profile) for the compare response's carrier block.
 * application.yaml: shiporbit.partners.profiles.<id>.display-name / initials / color / rating / etd-min-days / etd-max-days.
 * Constructor-bound record registered by @ConfigurationPropertiesScan (never also @Component).
 */
@ConfigurationProperties(prefix = "shiporbit.partners")
public record PartnerProfileProperties(Map<Integer, Profile> profiles) {

    public PartnerProfileProperties {
        profiles = profiles == null ? Map.of() : Map.copyOf(profiles);
    }

    public record Profile(String displayName, String initials, String color, BigDecimal rating,
                          Integer etdMinDays, Integer etdMaxDays) {

        /** [min, max] days, or null when not configured. */
        public List<Integer> etd() {
            return etdMinDays == null || etdMaxDays == null ? null : List.of(etdMinDays, etdMaxDays);
        }
    }

    /** Configured profile, or one derived from the partner's DB name (AMAZON_SHIPPING -> "Amazon Shipping", "AS"). */
    public Profile forPartner(Integer partnerId, String dbName) {
        Profile configured = profiles.get(partnerId);
        String fallbackName = prettify(dbName);
        if (configured == null) {
            return new Profile(fallbackName, initials(fallbackName), null, null, null, null);
        }
        String name = configured.displayName() != null ? configured.displayName() : fallbackName;
        return new Profile(name, configured.initials() != null ? configured.initials() : initials(name),
                configured.color(), configured.rating(), configured.etdMinDays(), configured.etdMaxDays());
    }

    private static String prettify(String dbName) {
        if (dbName == null || dbName.isBlank()) {
            return "Unknown";
        }
        StringBuilder out = new StringBuilder();
        for (String word : dbName.trim().toLowerCase().split("[_\\s]+")) {
            if (!word.isEmpty()) {
                out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return out.toString();
    }

    private static String initials(String name) {
        String[] words = name.split("\\s+");
        return words.length > 1
                ? ("" + words[0].charAt(0) + words[1].charAt(0)).toUpperCase()
                : name.substring(0, Math.min(2, name.length())).toUpperCase();
    }
}
