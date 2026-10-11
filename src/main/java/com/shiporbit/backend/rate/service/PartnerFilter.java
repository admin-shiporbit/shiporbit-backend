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
public final class PartnerFilter {

    private PartnerFilter() {
    }

    /** @param requested already upper-cased by QuoteRequest; empty = all partners (always valid) */
    public static void requireKnown(Set<String> requested, Collection<String> knownNames) {
        if (requested.isEmpty()) {
            return;
        }
        Set<String> known = knownNames.stream()
                .map(n -> n.toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(TreeSet::new));
        List<String> unknown = requested.stream().filter(n -> !known.contains(n)).sorted().toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown partner name(s) in 'partners': " + String.join(", ", unknown)
                    + ". Valid names: " + String.join(", ", known));
        }
    }
}
