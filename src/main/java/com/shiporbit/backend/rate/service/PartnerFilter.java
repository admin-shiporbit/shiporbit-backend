package com.shiporbit.backend.rate.service;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Checks the request's "partners" filter against SO_DELIVERY_PARTNER_LIST names.
 * An unknown name (e.g. "AMAZON" instead of "AMAZON_SHIPPING") used to match nothing and was
 * silently dropped; now it is a 400 (IllegalArgumentException -> GlobalExceptionHandler) that
 * lists the valid names.
 */
// Static helper used in Step 3a.
public final class PartnerFilter {

    // No instances.
    private PartnerFilter() {
    }

    /** @param requested already upper-cased by QuoteRequest; empty = all partners (always valid) */
    // Fail fast when the request names a partner that does not exist.
    public static void requireKnown(Set<String> requested, Collection<String> knownNames) {
        // Nothing named = all partners (the /compare case).
        if (requested.isEmpty()) {
            // Valid, return.
            return;
        }
        // Known names from the DB
        Set<String> known = knownNames.stream()
                // upper-cased,
                .map(n -> n.toUpperCase(Locale.ROOT))
                // in a sorted set (for the error message).
                .collect(Collectors.toCollection(TreeSet::new));
        // Requested names not in the DB, sorted.
        List<String> unknown = requested.stream().filter(n -> !known.contains(n)).sorted().toList();
        // Any?
        if (!unknown.isEmpty()) {
            // -> IllegalArgumentException -> 400 with the valid names.
            throw new IllegalArgumentException("Unknown partner name(s) in 'partners': " + String.join(", ", unknown)
                    + ". Valid names: " + String.join(", ", known));
        }
    }
}
