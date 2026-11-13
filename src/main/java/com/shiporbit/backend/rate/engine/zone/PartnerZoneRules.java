package com.shiporbit.backend.rate.engine.zone;

import com.shiporbit.backend.rate.engine.PincodeInfo;
import com.shiporbit.backend.rate.entity.ZoneCondition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Zone rules of one partner + pricing model: ordered rules, the groups they use, and pair lookups.
 * resolve() works out the slab LOCATION_KEY for a lane from the two SO_PARTNER_PINCODE rows,
 * so no row per pincode pair is needed (19,586 pincodes would be ~383 million pairs).
 * Chain of Responsibility: rules are tried in SEQ order and the first one that answers wins.
 */
// One partner + pricing model: ordered rules,
public record PartnerZoneRules(List<ZoneRule> rules,
                               // group name -> ZoneGroup,
                               Map<String, ZoneGroup> groups,
                               // group name -> (origin value, destination value) -> location key.
                               Map<String, Map<ValuePair, String>> pairs) {

    /** Origin and destination values of one group, the key of SO_ZONE_PAIR. */
    // Key of SO_ZONE_PAIR.
    public record ValuePair(String origin, String destination) { }

    // Compact constructor:
    public PartnerZoneRules {
        // rules sorted by SEQ,
        rules = rules.stream().sorted(Comparator.comparingInt(ZoneRule::seq)).toList();
        // immutable groups,
        groups = Map.copyOf(groups);
        // deep copy of pairs
        Map<String, Map<ValuePair, String>> copy = new HashMap<>();
        // (each inner map frozen),
        pairs.forEach((group, byPair) -> copy.put(group, Map.copyOf(byPair)));
        // outer map frozen.
        pairs = Map.copyOf(copy);
    }

    /** LOCATION_KEY for origin -> destination; empty when no rule matches (quote becomes ZONE_UNKNOWN). */
    // Step 3c: location key for the lane.
    public Optional<String> resolve(PincodeInfo origin, PincodeInfo destination) {
        // Try each rule in SEQ order (Chain of Responsibility).
        for (ZoneRule rule : rules) {
            // Evaluate one rule.
            Optional<String> key = apply(rule, origin, destination);
            // It answered?
            if (key.isPresent()) {
                // First answer wins.
                return key;
            }
        }
        // No rule matched -> empty -> quote becomes ZONE_UNKNOWN.
        return Optional.empty();
    }

    // Evaluate one rule.
    private Optional<String> apply(ZoneRule rule, PincodeInfo origin, PincodeInfo destination) {
        // Origin's value in the rule's group (city match first, then state), e.g. W1.
        Optional<String> originValue = valueOf(rule.groupName(), origin);
        // Destination's value, e.g. N1.
        Optional<String> destValue = valueOf(rule.groupName(), destination);

        // PAIR rule (XpressBees uses this): key comes from SO_ZONE_PAIR.
        if (rule.condition() == ZoneCondition.PAIR) {
            // Either pincode not in the group?
            if (originValue.isEmpty() || destValue.isEmpty()) {
                // -> no answer (e.g. Daman / Puducherry for XpressBees).
                return Optional.empty();
            }
            // Look up (origin value, destination value) in the pairs of this group
            return Optional.ofNullable(pairs.getOrDefault(rule.groupName(), Map.of())
                    // -> e.g. ("W1","N1") -> "W1-N1"; null -> empty.
                    .get(new ValuePair(originValue.get(), destValue.get())));
        }

        // Other conditions: does the rule match?
        boolean matches = switch (rule.condition()) {
            // ANY: always.
            case ANY -> true;
            // SAME_STATE: both pincodes in the same state.
            case SAME_STATE -> origin.stateId() != null && origin.stateId().equals(destination.stateId());
            // SAME_GROUP: both have the same value in the group.
            case SAME_GROUP -> originValue.isPresent() && originValue.equals(destValue);
            // BOTH_IN: both are in the group.
            case BOTH_IN -> originValue.isPresent() && destValue.isPresent();
            // ORIGIN_IN: origin is in the group.
            case ORIGIN_IN -> originValue.isPresent();
            // DEST_IN: destination is in the group.
            case DEST_IN -> destValue.isPresent();
            // EITHER_IN: at least one is in the group.
            case EITHER_IN -> originValue.isPresent() || destValue.isPresent();
            // PAIR: never reaches here.
            case PAIR -> false;                     // handled above
        };
        // Match -> the rule's fixed location key; else no answer.
        return matches ? Optional.of(rule.locationKey()) : Optional.empty();
    }

    // A pincode's value in a group.
    private Optional<String> valueOf(String groupName, PincodeInfo pincode) {
        // Group by name (null name -> none).
        ZoneGroup group = groupName == null ? null : groups.get(groupName);
        // No group -> empty; else ZoneGroup.valueOf (city first, then state).
        return group == null ? Optional.empty() : group.valueOf(pincode);
    }

    /**
     * Configuration mistakes the database constraints can't catch. Logged as warnings on load;
     * each one would otherwise show up only as a wrong or missing zone on some lanes.
     */
    // Startup / load-time checks (ZoneConfigLoader logs them). Not used per request.
    public List<String> problems() {
        // Collected messages.
        List<String> problems = new ArrayList<>();
        // For each rule:
        for (ZoneRule rule : rules) {
            // does it need a group?
            boolean needsGroup = rule.condition() != ZoneCondition.ANY && rule.condition() != ZoneCondition.SAME_STATE;
            // Needs one but none exists?
            if (needsGroup && !groups.containsKey(rule.groupName())) {
                // Record the problem.
                problems.add("rule " + rule.seq() + " (" + rule.condition() + ") uses group '" + rule.groupName()
                        + "', which has no SO_ZONE_GROUP rows");
            }
            // PAIR without pair rows?
            if (rule.condition() == ZoneCondition.PAIR && !pairs.containsKey(rule.groupName())) {
                // Record the problem.
                problems.add("rule " + rule.seq() + " (PAIR) has no SO_ZONE_PAIR rows for group '" + rule.groupName() + "'");
            }
        }
        // For each pair group:
        pairs.forEach((groupName, byPair) -> {
            // its group.
            ZoneGroup group = groups.get(groupName);
            // Missing?
            if (group == null) {
                // Record
                problems.add("SO_ZONE_PAIR rows for group '" + groupName + "', which has no SO_ZONE_GROUP rows");
                // and continue with the next group.
                return;
            }
            // For each pair:
            byPair.keySet().forEach(pair -> {
                // values not produced by the group (typo)?
                if (!group.values().contains(pair.origin()) || !group.values().contains(pair.destination())) {
                    // Record.
                    problems.add("SO_ZONE_PAIR " + pair.origin() + " -> " + pair.destination()
                            + " uses a value not in group '" + groupName + "' (typo?)");
                }
            });
        });
        // Return all problems.
        return problems;
    }
}
