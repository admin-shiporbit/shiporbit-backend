package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.rate.cache.RateCacheConfig;
import com.shiporbit.backend.rate.engine.zone.PartnerZoneRules;
import com.shiporbit.backend.rate.engine.zone.PartnerZoneRules.ValuePair;
import com.shiporbit.backend.rate.engine.zone.ZoneConfig;
import com.shiporbit.backend.rate.engine.zone.ZoneGroup;
import com.shiporbit.backend.rate.engine.zone.ZoneRule;
import com.shiporbit.backend.rate.entity.PricingModel;
import com.shiporbit.backend.rate.entity.ZoneGroupEntity;
import com.shiporbit.backend.rate.entity.ZonePairEntity;
import com.shiporbit.backend.rate.entity.ZoneRuleEntity;
import com.shiporbit.backend.rate.repository.ZoneGroupRepository;
import com.shiporbit.backend.rate.repository.ZonePairRepository;
import com.shiporbit.backend.rate.repository.ZoneRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads SO_ZONE_GROUP, SO_ZONE_RULE and SO_ZONE_PAIR (3 queries, a few hundred rows) into one immutable ZoneConfig.
 * Its own bean so RateCardLoader.lanes() calls it through Spring's cache proxy (see PartnerDirectory).
 * Configuration mistakes the DB constraints can't catch are logged as warnings on every load.
 */
// Spring service bean (separate bean so the @Cacheable proxy applies when RateCardLoader calls it).
@Service
public class ZoneConfigLoader {

    // Logger.
    private static final Logger LOGGER = LoggerFactory.getLogger(ZoneConfigLoader.class);

    // SO_ZONE_GROUP.
    private final ZoneGroupRepository groupRepo;
    // SO_ZONE_RULE.
    private final ZoneRuleRepository ruleRepo;
    // SO_ZONE_PAIR.
    private final ZonePairRepository pairRepo;

    // Constructor injection.
    public ZoneConfigLoader(ZoneGroupRepository groupRepo, ZoneRuleRepository ruleRepo, ZonePairRepository pairRepo) {
        // Store.
        this.groupRepo = groupRepo;
        // Store.
        this.ruleRepo = ruleRepo;
        // Store.
        this.pairRepo = pairRepo;
    }

    /** Partner + pricing model: zone rules are configured per pair. */
    // Map key: partner + pricing model.
    private record Key(Integer partnerId, PricingModel model) { }

    /** Cached zone rules, used on every quote. Cleared by RateCardCacheService.evictAll(). */
    // Cached (RateCacheConfig.ZONE_CONFIG): loaded once, reused by every lane (Step 3c).
    @Cacheable(RateCacheConfig.ZONE_CONFIG)
    // Read-only transaction.
    @Transactional(readOnly = true)
    public ZoneConfig current() {
        // Debug log (cache miss only).
        LOGGER.debug("Rate-card cache MISS: loading zone rules from DB");
        // Read from DB.
        return read();
    }

    /** Uncached read, for the inspection endpoint, which always shows current DB data. */
    // Read-only transaction.
    @Transactional(readOnly = true)
    // Builds ZoneConfig from the three tables.
    public ZoneConfig read() {
        // (partner, model) -> ordered rules.
        Map<Key, List<ZoneRule>> rules = new HashMap<>();
        // Query 1: all SO_ZONE_RULE rows.
        for (ZoneRuleEntity r : ruleRepo.findAll()) {
            // Group by (partner, model),
            rules.computeIfAbsent(new Key(r.getPartnerId(), r.getPricingModel()), k -> new ArrayList<>())
                    // ZoneRule(seq, condition ANY / SAME_STATE / SAME_GROUP / BOTH_IN / ORIGIN_IN / DEST_IN / EITHER_IN / PAIR, group name, location key).
                    .add(new ZoneRule(r.getSeq(), r.getMatchCondition(), r.getGroupName(), r.getLocationKey()));
        }

        // key -> group name -> city / state -> value
        // (partner, model) -> group -> city code -> group value.
        Map<Key, Map<String, Map<String, String>>> byCity = new HashMap<>();
        // (partner, model) -> group -> state id -> group value.
        Map<Key, Map<String, Map<Integer, String>>> byState = new HashMap<>();
        // Query 2: all SO_ZONE_GROUP rows.
        for (ZoneGroupEntity g : groupRepo.findAll()) {
            // Key of the row.
            Key key = new Key(g.getPartnerId(), g.getPricingModel());
            // Row for a city?
            if (g.getCityCode() != null) {
                // Put under byCity: group -> city -> value
                byCity.computeIfAbsent(key, k -> new HashMap<>()).computeIfAbsent(g.getGroupName(), k -> new HashMap<>())
                        // (e.g. REGION / MUMBAI -> W1).
                        .put(g.getCityCode(), g.getGroupValue());
            // Otherwise for a state:
            } else {
                // put under byState: group -> state -> value
                byState.computeIfAbsent(key, k -> new HashMap<>()).computeIfAbsent(g.getGroupName(), k -> new HashMap<>())
                        // (e.g. REGION / 32 (Delhi) -> N1).
                        .put(g.getStateId(), g.getGroupValue());
            }
        }

        // (partner, model) -> group -> (origin value, destination value) -> location key.
        Map<Key, Map<String, Map<ValuePair, String>>> pairs = new HashMap<>();
        // Query 3: all SO_ZONE_PAIR rows.
        for (ZonePairEntity p : pairRepo.findAll()) {
            // Group by (partner, model),
            pairs.computeIfAbsent(new Key(p.getPartnerId(), p.getPricingModel()), k -> new HashMap<>())
                    // then by group name,
                    .computeIfAbsent(p.getGroupName(), k -> new HashMap<>())
                    // (origin value, destination value) -> location key.
                    .put(new ValuePair(p.getOriginValue(), p.getDestinationValue()), p.getLocationKey());
        }

        // Every (partner, model) seen in any table:
        Set<Key> keys = new HashSet<>(rules.keySet());
        // plus groups by city,
        keys.addAll(byCity.keySet());
        // plus groups by state,
        keys.addAll(byState.keySet());
        // plus pairs.
        keys.addAll(pairs.keySet());

        // partner -> model -> PartnerZoneRules.
        Map<Integer, Map<PricingModel, PartnerZoneRules>> partners = new HashMap<>();
        // For each (partner, model):
        for (Key key : keys) {
            // groups/pairs without rules can never be used
            if (!rules.containsKey(key)) {
                // -> warn
                LOGGER.warn("Zone groups/pairs for partner {} {} have no SO_ZONE_RULE rows: ignored", key.partnerId(), key.model());
                // and skip.
                continue;
            }
            // All group names used by this key (cities)
            Set<String> groupNames = new HashSet<>(byCity.getOrDefault(key, Map.of()).keySet());
            // + states.
            groupNames.addAll(byState.getOrDefault(key, Map.of()).keySet());
            // Group name -> ZoneGroup.
            Map<String, ZoneGroup> groups = new HashMap<>();
            // For each group name:
            for (String name : groupNames) {
                // build ZoneGroup(name,
                groups.put(name, new ZoneGroup(name,
                        // city -> value map,
                        byCity.getOrDefault(key, Map.of()).getOrDefault(name, Map.of()),
                        // state -> value map).
                        byState.getOrDefault(key, Map.of()).getOrDefault(name, Map.of())));
            }
            // Rules + groups + pairs for this partner/model.
            PartnerZoneRules partnerRules = new PartnerZoneRules(rules.get(key), groups, pairs.getOrDefault(key, Map.of()));
            // Config mistakes (rule referencing a missing group etc.)
            partnerRules.problems().forEach(problem ->
                    // -> warning.
                    LOGGER.warn("Zone rules for partner {} {}: {}", key.partnerId(), key.model(), problem));
            // Add under partner -> model.
            partners.computeIfAbsent(key.partnerId(), k -> new HashMap<>()).put(key.model(), partnerRules);
        }
        // Immutable ZoneConfig.
        return new ZoneConfig(partners);
    }
}
