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
@Service
public class ZoneConfigLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZoneConfigLoader.class);

    private final ZoneGroupRepository groupRepo;
    private final ZoneRuleRepository ruleRepo;
    private final ZonePairRepository pairRepo;

    public ZoneConfigLoader(ZoneGroupRepository groupRepo, ZoneRuleRepository ruleRepo, ZonePairRepository pairRepo) {
        this.groupRepo = groupRepo;
        this.ruleRepo = ruleRepo;
        this.pairRepo = pairRepo;
    }

    /** Partner + pricing model: zone rules are configured per pair. */
    private record Key(Integer partnerId, PricingModel model) { }

    /** Cached zone rules, used on every quote. Cleared by RateCardCacheService.evictAll(). */
    @Cacheable(RateCacheConfig.ZONE_CONFIG)
    @Transactional(readOnly = true)
    public ZoneConfig current() {
        LOGGER.debug("Rate-card cache MISS: loading zone rules from DB");
        return read();
    }

    /** Uncached read, for the inspection endpoint, which always shows current DB data. */
    @Transactional(readOnly = true)
    public ZoneConfig read() {
        Map<Key, List<ZoneRule>> rules = new HashMap<>();
        for (ZoneRuleEntity r : ruleRepo.findAll()) {
            rules.computeIfAbsent(new Key(r.getPartnerId(), r.getPricingModel()), k -> new ArrayList<>())
                    .add(new ZoneRule(r.getSeq(), r.getMatchCondition(), r.getGroupName(), r.getLocationKey()));
        }

        // key -> group name -> city / state -> value
        Map<Key, Map<String, Map<String, String>>> byCity = new HashMap<>();
        Map<Key, Map<String, Map<Integer, String>>> byState = new HashMap<>();
        for (ZoneGroupEntity g : groupRepo.findAll()) {
            Key key = new Key(g.getPartnerId(), g.getPricingModel());
            if (g.getCityCode() != null) {
                byCity.computeIfAbsent(key, k -> new HashMap<>()).computeIfAbsent(g.getGroupName(), k -> new HashMap<>())
                        .put(g.getCityCode(), g.getGroupValue());
            } else {
                byState.computeIfAbsent(key, k -> new HashMap<>()).computeIfAbsent(g.getGroupName(), k -> new HashMap<>())
                        .put(g.getStateId(), g.getGroupValue());
            }
        }

        Map<Key, Map<String, Map<ValuePair, String>>> pairs = new HashMap<>();
        for (ZonePairEntity p : pairRepo.findAll()) {
            pairs.computeIfAbsent(new Key(p.getPartnerId(), p.getPricingModel()), k -> new HashMap<>())
                    .computeIfAbsent(p.getGroupName(), k -> new HashMap<>())
                    .put(new ValuePair(p.getOriginValue(), p.getDestinationValue()), p.getLocationKey());
        }

        Set<Key> keys = new HashSet<>(rules.keySet());
        keys.addAll(byCity.keySet());
        keys.addAll(byState.keySet());
        keys.addAll(pairs.keySet());

        Map<Integer, Map<PricingModel, PartnerZoneRules>> partners = new HashMap<>();
        for (Key key : keys) {
            if (!rules.containsKey(key)) {
                LOGGER.warn("Zone groups/pairs for partner {} {} have no SO_ZONE_RULE rows: ignored", key.partnerId(), key.model());
                continue;
            }
            Set<String> groupNames = new HashSet<>(byCity.getOrDefault(key, Map.of()).keySet());
            groupNames.addAll(byState.getOrDefault(key, Map.of()).keySet());
            Map<String, ZoneGroup> groups = new HashMap<>();
            for (String name : groupNames) {
                groups.put(name, new ZoneGroup(name,
                        byCity.getOrDefault(key, Map.of()).getOrDefault(name, Map.of()),
                        byState.getOrDefault(key, Map.of()).getOrDefault(name, Map.of())));
            }
            PartnerZoneRules partnerRules = new PartnerZoneRules(rules.get(key), groups, pairs.getOrDefault(key, Map.of()));
            partnerRules.problems().forEach(problem ->
                    LOGGER.warn("Zone rules for partner {} {}: {}", key.partnerId(), key.model(), problem));
            partners.computeIfAbsent(key.partnerId(), k -> new HashMap<>()).put(key.model(), partnerRules);
        }
        return new ZoneConfig(partners);
    }
}
