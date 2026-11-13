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
// Bound from shiporbit.partners.profiles in application.yaml at startup.
@ConfigurationProperties(prefix = "shiporbit.partners")
// partner id -> profile.
public record PartnerProfileProperties(Map<Integer, Profile> profiles) {

    // Compact constructor:
    public PartnerProfileProperties {
        // null -> empty; immutable.
        profiles = profiles == null ? Map.of() : Map.copyOf(profiles);
    }

    // One profile: display name, initials, colour, rating,
    public record Profile(String displayName, String initials, String color, BigDecimal rating,
                          // ETD min / max days.
                          Integer etdMinDays, Integer etdMaxDays) {

        /** [min, max] days, or null when not configured. */
        // ETD for the response.
        public List<Integer> etd() {
            // Both set -> [min, max], else null.
            return etdMinDays == null || etdMaxDays == null ? null : List.of(etdMinDays, etdMaxDays);
        }
    }

    /** Configured profile, or one derived from the partner's DB name (AMAZON_SHIPPING -> "Amazon Shipping", "AS"). */
    // Profile for a partner (Step 3 loop in RateCompareService).
    public Profile forPartner(Integer partnerId, String dbName) {
        // Configured one, if any.
        Profile configured = profiles.get(partnerId);
        // Name from the DB, prettified (XPRESSBEES -> "Xpressbees").
        String fallbackName = prettify(dbName);
        // Not configured?
        if (configured == null) {
            // -> derived name + initials, no colour / rating / ETD.
            return new Profile(fallbackName, initials(fallbackName), null, null, null, null);
        }
        // Configured name or the derived one.
        String name = configured.displayName() != null ? configured.displayName() : fallbackName;
        // Configured initials or derived,
        return new Profile(name, configured.initials() != null ? configured.initials() : initials(name),
                // colour, rating, ETD from config.
                configured.color(), configured.rating(), configured.etdMinDays(), configured.etdMaxDays());
    }

    // DB name -> display name.
    private static String prettify(String dbName) {
        // Blank?
        if (dbName == null || dbName.isBlank()) {
            // -> "Unknown".
            return "Unknown";
        }
        // Builder.
        StringBuilder out = new StringBuilder();
        // Each word (split on _ or spaces, lower case):
        for (String word : dbName.trim().toLowerCase().split("[_\\s]+")) {
            // non-empty?
            if (!word.isEmpty()) {
                // space between words, first letter upper case, rest as is.
                out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        // Result.
        return out.toString();
    }

    // Initials.
    private static String initials(String name) {
        // Words.
        String[] words = name.split("\\s+");
        // Two or more words?
        return words.length > 1
                // -> first letters of the first two,
                ? ("" + words[0].charAt(0) + words[1].charAt(0)).toUpperCase()
                // else first two letters; upper case.
                : name.substring(0, Math.min(2, name.length())).toUpperCase();
    }
}
