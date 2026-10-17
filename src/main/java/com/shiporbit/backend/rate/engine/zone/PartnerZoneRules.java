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
public record PartnerZoneRules(List<ZoneRule> rules,
                               Map<String, ZoneGroup> groups,
                               Map<String, Map<ValuePair, String>> pairs) {

    /** Origin and destination values of one group, the key of SO_ZONE_PAIR. */
    public record ValuePair(String origin, String destination) { }

    public PartnerZoneRules {
        rules = rules.stream().sorted(Comparator.comparingInt(ZoneRule::seq)).toList();
        groups = Map.copyOf(groups);
        Map<String, Map<ValuePair, String>> copy = new HashMap<>();
        pairs.forEach((group, byPair) -> copy.put(group, Map.copyOf(byPair)));
        pairs = Map.copyOf(copy);
    }

    /** LOCATION_KEY for origin -> destination; empty when no rule matches (quote becomes ZONE_UNKNOWN). */
    public Optional<String> resolve(PincodeInfo origin, PincodeInfo destination) {
        for (ZoneRule rule : rules) {
            Optional<String> key = apply(rule, origin, destination);
            if (key.isPresent()) {
                return key;
            }
        }
        return Optional.empty();
    }

    private Optional<String> apply(ZoneRule rule, PincodeInfo origin, PincodeInfo destination) {
        Optional<String> originValue = valueOf(rule.groupName(), origin);
        Optional<String> destValue = valueOf(rule.groupName(), destination);

        if (rule.condition() == ZoneCondition.PAIR) {
            if (originValue.isEmpty() || destValue.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(pairs.getOrDefault(rule.groupName(), Map.of())
                    .get(new ValuePair(originValue.get(), destValue.get())));
        }

        boolean matches = switch (rule.condition()) {
            case ANY -> true;
            case SAME_STATE -> origin.stateId() != null && origin.stateId().equals(destination.stateId());
            case SAME_GROUP -> originValue.isPresent() && originValue.equals(destValue);
            case BOTH_IN -> originValue.isPresent() && destValue.isPresent();
            case ORIGIN_IN -> originValue.isPresent();
            case DEST_IN -> destValue.isPresent();
            case EITHER_IN -> originValue.isPresent() || destValue.isPresent();
            case PAIR -> false;                     // handled above
        };
        return matches ? Optional.of(rule.locationKey()) : Optional.empty();
    }

    private Optional<String> valueOf(String groupName, PincodeInfo pincode) {
        ZoneGroup group = groupName == null ? null : groups.get(groupName);
        return group == null ? Optional.empty() : group.valueOf(pincode);
    }

    /**
     * Configuration mistakes the database constraints can't catch. Logged as warnings on load;
     * each one would otherwise show up only as a wrong or missing zone on some lanes.
     */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        for (ZoneRule rule : rules) {
            boolean needsGroup = rule.condition() != ZoneCondition.ANY && rule.condition() != ZoneCondition.SAME_STATE;
            if (needsGroup && !groups.containsKey(rule.groupName())) {
                problems.add("rule " + rule.seq() + " (" + rule.condition() + ") uses group '" + rule.groupName()
                        + "', which has no SO_ZONE_GROUP rows");
            }
            if (rule.condition() == ZoneCondition.PAIR && !pairs.containsKey(rule.groupName())) {
                problems.add("rule " + rule.seq() + " (PAIR) has no SO_ZONE_PAIR rows for group '" + rule.groupName() + "'");
            }
        }
        pairs.forEach((groupName, byPair) -> {
            ZoneGroup group = groups.get(groupName);
            if (group == null) {
                problems.add("SO_ZONE_PAIR rows for group '" + groupName + "', which has no SO_ZONE_GROUP rows");
                return;
            }
            byPair.keySet().forEach(pair -> {
                if (!group.values().contains(pair.origin()) || !group.values().contains(pair.destination())) {
                    problems.add("SO_ZONE_PAIR " + pair.origin() + " -> " + pair.destination()
                            + " uses a value not in group '" + groupName + "' (typo?)");
                }
            });
        });
        return problems;
    }
}
