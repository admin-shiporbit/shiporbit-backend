package com.shiporbit.backend.rate.routing;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Which partners may be quoted (POST /api/v1/rates/quotes and /api/v1/rates/compare).
 * application.yaml:
 * <pre>
 * shiporbit.partners.enabled:  [AMAZON_SHIPPING, DTDC, ...]   # quoted
 * shiporbit.partners.disabled: [DELHIVERY]                    # skipped completely
 * </pre>
 * Names = SO_DELIVERY_PARTNER_LIST.PARTNER_NAME, case-insensitive.
 * <ul>
 *   <li>In disabled: never quoted (wins if a name is in both lists).</li>
 *   <li>In enabled: quoted (live API or rate card).</li>
 *   <li>In neither: not quoted (logged as a warning).</li>
 *   <li>enabled key missing: every partner not in disabled is quoted.</li>
 * </ul>
 */
// Spring binds shiporbit.partners.enabled / disabled from application.yaml into this record at startup.
@ConfigurationProperties(prefix = "shiporbit.partners")
// enabled = partners that may be quoted; disabled = never quoted.
public record PartnerAllowlistProperties(List<String> enabled, List<String> disabled) {

    // Compact constructor: normalise the lists once.
    public PartnerAllowlistProperties {
        // Trim, upper-case, drop blanks and duplicates (null stays null = "not configured").
        enabled = clean(enabled);
        // Missing disabled -> empty list.
        disabled = disabled == null ? List.of() : clean(disabled);
    }

    /** False when shiporbit.partners.enabled is not set (then every partner not disabled is allowed). */
    // Is enabled set at all?
    public boolean configured() {
        // null = not set.
        return enabled != null;
    }

    // Used in Step 3b (rate cards) and 3d (live APIs): may this partner be quoted?
    public boolean allows(String partnerName) {
        // Unknown name, or in disabled?
        if (partnerName == null || contains(disabled, partnerName)) {
            // -> no (disabled wins over enabled).
            return false;
        }
        // enabled not set -> yes; else only when listed in enabled.
        return enabled == null || contains(enabled, partnerName);
    }

    /** on = enabled minus disabled; off = disabled. For the startup log. */
    // For the startup log.
    public List<String> names(boolean on) {
        // OFF list asked?
        if (!on) {
            // -> disabled.
            return disabled;
        }
        // ON = enabled minus disabled.
        return enabled == null ? List.of() : enabled.stream().filter(n -> !contains(disabled, n)).toList();
    }

    /** Names written in both lists (disabled wins). */
    // Names in both lists.
    public List<String> inBoth() {
        // enabled filtered by disabled.
        return enabled == null ? List.of() : enabled.stream().filter(n -> contains(disabled, n)).toList();
    }

    /** Every name in either list. */
    // All configured names.
    public List<String> allNames() {
        // enabled + disabled, distinct.
        return Stream.concat(enabled == null ? Stream.empty() : enabled.stream(), disabled.stream()).distinct().toList();
    }

    /** Same partner name, ignoring case, spaces, '_' and '-'. */
    // Name comparison used everywhere.
    public static boolean sameName(String a, String b) {
        // Equal after normalise().
        return Objects.equals(normalise(a), normalise(b));
    }

    // Does the list contain this partner?
    private static boolean contains(List<String> names, String partnerName) {
        // Any name matching.
        return names.stream().anyMatch(n -> sameName(n, partnerName));
    }

    // List cleanup:
    private static List<String> clean(List<String> names) {
        // null stays null; else
        return names == null ? null : names.stream()
                // drop null / blank,
                .filter(n -> n != null && !normalise(n).isEmpty())
                // trim + upper-case,
                .map(n -> n.trim().toUpperCase(Locale.ROOT))
                // drop duplicates,
                .distinct()
                // to list.
                .toList();
    }

    // "Amazon Shipping" / "AMAZON_SHIPPING" -> "AMAZONSHIPPING".
    private static String normalise(String name) {
        // Upper-case and keep only letters and digits.
        return name == null ? "" : name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
