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
@ConfigurationProperties(prefix = "shiporbit.partners")
public record PartnerAllowlistProperties(List<String> enabled, List<String> disabled) {

    public PartnerAllowlistProperties {
        enabled = clean(enabled);
        disabled = disabled == null ? List.of() : clean(disabled);
    }

    /** False when shiporbit.partners.enabled is not set (then every partner not disabled is allowed). */
    public boolean configured() {
        return enabled != null;
    }

    public boolean allows(String partnerName) {
        if (partnerName == null || contains(disabled, partnerName)) {
            return false;
        }
        return enabled == null || contains(enabled, partnerName);
    }

    /** on = enabled minus disabled; off = disabled. For the startup log. */
    public List<String> names(boolean on) {
        if (!on) {
            return disabled;
        }
        return enabled == null ? List.of() : enabled.stream().filter(n -> !contains(disabled, n)).toList();
    }

    /** Names written in both lists (disabled wins). */
    public List<String> inBoth() {
        return enabled == null ? List.of() : enabled.stream().filter(n -> contains(disabled, n)).toList();
    }

    /** Every name in either list. */
    public List<String> allNames() {
        return Stream.concat(enabled == null ? Stream.empty() : enabled.stream(), disabled.stream()).distinct().toList();
    }

    /** Same partner name, ignoring case, spaces, '_' and '-'. */
    public static boolean sameName(String a, String b) {
        return Objects.equals(normalise(a), normalise(b));
    }

    private static boolean contains(List<String> names, String partnerName) {
        return names.stream().anyMatch(n -> sameName(n, partnerName));
    }

    private static List<String> clean(List<String> names) {
        return names == null ? null : names.stream()
                .filter(n -> n != null && !normalise(n).isEmpty())
                .map(n -> n.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private static String normalise(String name) {
        return name == null ? "" : name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
