package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.rate.core.WeightRules;
import com.shiporbit.backend.rate.engine.ChargeRule;
import com.shiporbit.backend.rate.engine.PartnerLane;
import com.shiporbit.backend.rate.engine.PincodeInfo;
import com.shiporbit.backend.rate.engine.RateCardSnapshot;
import com.shiporbit.backend.rate.engine.ServiceInfo;
import com.shiporbit.backend.rate.engine.SlabRow;
import com.shiporbit.backend.rate.engine.zone.ZoneConfig;
import com.shiporbit.backend.rate.entity.PartnerPincodeEntity;
import com.shiporbit.backend.rate.entity.PricingModel;
import com.shiporbit.backend.rate.entity.RateCardEntity;
import com.shiporbit.backend.rate.entity.RateChargeRuleEntity;
import com.shiporbit.backend.rate.entity.RateServiceEntity;
import com.shiporbit.backend.rate.entity.RateSlabEntity;
import com.shiporbit.backend.rate.entity.ZoneMapEntity;
import com.shiporbit.backend.rate.repository.PartnerPincodeRepository;
import com.shiporbit.backend.rate.repository.RateCardRepository;
import com.shiporbit.backend.rate.repository.RateChargeRuleRepository;
import com.shiporbit.backend.rate.repository.RateServiceRepository;
import com.shiporbit.backend.rate.repository.RateSlabRepository;
import com.shiporbit.backend.rate.repository.ZoneMapRepository;
import com.shiporbit.backend.rate.cache.PartnerDirectory;
import com.shiporbit.backend.rate.cache.RateCacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads rate-card tables and turns them into immutable engine records.
 * Query count is fixed, whatever the number of services:
 * services, partners, ACTIVE cards, slabs, rules (5) + pincodes, zones (2) per lane
 * + zone rules (3, ZoneConfigLoader, cached once for all lanes).
 * Results are cached (RateCacheConfig): servicesOn by date, lanes by origin/destination;
 * zone rules via ZoneConfigLoader;
 * partner names via PartnerDirectory.
 * Everything returned is immutable, so cached values can be shared safely.
 * After changing rate data, call RateCardCacheService.evictAll().
 */
@Service
@Transactional(readOnly = true)
public class RateCardLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateCardLoader.class);

    private final RateServiceRepository serviceRepo;
    private final RateCardRepository cardRepo;
    private final RateSlabRepository slabRepo;
    private final RateChargeRuleRepository ruleRepo;
    private final PartnerPincodeRepository pincodeRepo;
    private final ZoneMapRepository zoneRepo;
    private final PartnerDirectory partnerDirectory;
    private final ZoneConfigLoader zoneConfigLoader;

    public RateCardLoader(RateServiceRepository serviceRepo, RateCardRepository cardRepo, RateSlabRepository slabRepo,
                          RateChargeRuleRepository ruleRepo, PartnerPincodeRepository pincodeRepo,
                          ZoneMapRepository zoneRepo, PartnerDirectory partnerDirectory,
                          ZoneConfigLoader zoneConfigLoader) {
        this.serviceRepo = serviceRepo;
        this.cardRepo = cardRepo;
        this.slabRepo = slabRepo;
        this.ruleRepo = ruleRepo;
        this.pincodeRepo = pincodeRepo;
        this.zoneRepo = zoneRepo;
        this.partnerDirectory = partnerDirectory;
        this.zoneConfigLoader = zoneConfigLoader;
    }

    /** One enabled service and its ACTIVE card on the date (card null = none active). */
    public record LoadedService(ServiceInfo service, RateCardSnapshot card) { }

    /** Every enabled service with its ACTIVE card on the date, sorted by partner id, service code. Cached per date. */
    @Cacheable(RateCacheConfig.SERVICES)
    public List<LoadedService> servicesOn(LocalDate date) {
        // Logged only on a cache miss: if this line is absent for a quote, cards came from the cache.
        LOGGER.debug("Rate-card cache MISS: loading services and ACTIVE cards for {} from DB", date);
        Map<Integer, String> partnerNames = partnerDirectory.names();   // other bean -> goes through the cache
        Map<Long, RateCardEntity> cardByService = cardRepo.findAllActiveOn(date).stream()
                .collect(Collectors.toMap(RateCardEntity::getServiceId, Function.identity()));

        Set<Long> cardIds = cardByService.values().stream().map(RateCardEntity::getId).collect(Collectors.toSet());
        Map<Long, List<RateSlabEntity>> slabsByCard = cardIds.isEmpty() ? Map.of()
                : slabRepo.findByRateCardIdIn(cardIds).stream().collect(Collectors.groupingBy(RateSlabEntity::getRateCardId));
        Map<Long, List<RateChargeRuleEntity>> rulesByCard = cardIds.isEmpty() ? Map.of()
                : ruleRepo.findByRateCardIdIn(cardIds).stream().collect(Collectors.groupingBy(RateChargeRuleEntity::getRateCardId));

        return serviceRepo.findByEnabledTrue().stream()
                .sorted(Comparator.comparing(RateServiceEntity::getPartnerId).thenComparing(RateServiceEntity::getServiceCode))
                .map(s -> {
                    ServiceInfo info = new ServiceInfo(s.getId(), s.getPartnerId(), partnerNames.get(s.getPartnerId()),
                            s.getServiceCode(), s.getPricingModel(), s.getMode());
                    RateCardEntity card = cardByService.get(s.getId());
                    return new LoadedService(info, card == null ? null : snapshot(info, card,
                            slabsByCard.getOrDefault(card.getId(), List.of()),
                            rulesByCard.getOrDefault(card.getId(), List.of())));
                })
                .toList();
    }

    /** SO_DELIVERY_PARTNER_LIST id -> name. Delegates to the cached PartnerDirectory. */
    public Map<Integer, String> partnerNames() {
        return partnerDirectory.names();     // cached in PartnerDirectory
    }

    /**
     * Pincode flags and zone keys for origin -> destination, per partner. Two queries. Cached per lane.
     * Zone key per pricing model: the SO_ZONE_MAP row for the exact pincodes if there is one (override),
     * otherwise the partner's zone rules (SO_ZONE_RULE), worked out from both pincodes' city and state.
     */
    @Cacheable(RateCacheConfig.LANES)
    public Map<Integer, PartnerLane> lanes(String origin, String destination) {
        LOGGER.debug("Rate-card cache MISS: loading lane {} -> {} from DB", origin, destination);
        Map<Integer, Map<String, PincodeInfo>> pins = new HashMap<>();
        for (PartnerPincodeEntity p : pincodeRepo.findByIdPincodeIn(Set.copyOf(List.of(origin, destination)))) {
            pins.computeIfAbsent(p.getId().getPartnerId(), k -> new HashMap<>()).put(p.getId().getPincode(), toPincode(p));
        }
        Map<Integer, Map<PricingModel, String>> keys = new HashMap<>();
        for (ZoneMapEntity z : zoneRepo.findByIdOriginPincodeAndIdDestinationPincode(origin, destination)) {
            keys.computeIfAbsent(z.getId().getPartnerId(), k -> new EnumMap<>(PricingModel.class))
                    .put(z.getId().getPricingModel(), z.getLocationKey());
        }

        ZoneConfig zoneConfig = zoneConfigLoader.current();    // other bean -> goes through the cache

        Map<Integer, PartnerLane> lanes = new HashMap<>();
        Set<Integer> partnerIds = new HashSet<>(pins.keySet());
        partnerIds.addAll(keys.keySet());
        for (Integer id : partnerIds) {
            Map<String, PincodeInfo> p = pins.getOrDefault(id, Map.of());
            PincodeInfo from = p.get(origin);
            PincodeInfo to = p.get(destination);
            // Not new EnumMap<>(Map.of()): that constructor throws on an empty non-EnumMap.
            Map<PricingModel, String> partnerKeys = new EnumMap<>(PricingModel.class);
            partnerKeys.putAll(keys.getOrDefault(id, Map.of()));
            if (from != null && to != null) {          // rules need both pincodes; a missing one is NOT_SERVICEABLE anyway
                for (PricingModel model : zoneConfig.modelsFor(id)) {
                    if (!partnerKeys.containsKey(model)) {      // SO_ZONE_MAP wins
                        zoneConfig.resolve(id, model, from, to).ifPresent(key -> partnerKeys.put(model, key));
                    }
                }
            }
            lanes.put(id, new PartnerLane(from, to, partnerKeys));
        }
        return Map.copyOf(lanes);
    }

    // ---- entity -> engine record mapping ----

    private static RateCardSnapshot snapshot(ServiceInfo info, RateCardEntity c,
                                             List<RateSlabEntity> slabs, List<RateChargeRuleEntity> rules) {
        return new RateCardSnapshot(info, c.getId(), c.getVersionLabel(), c.getDataStatus(),
                new WeightRules(c.getVolumetricDivisor(), c.getCftKg(), c.getMinChargeableWeightKg(), c.getMpsRule()),
                c.getMaxWeightKg(), c.getMaxDistanceKm(), c.getRtoRule(), c.getRtoPercent(), c.getMinBill(),
                c.isRatesIncludeGst(), c.getRounding(),
                slabs.stream().map(RateCardLoader::toSlab).toList(),
                rules.stream().map(RateCardLoader::toRule).toList());
    }

    private static SlabRow toSlab(RateSlabEntity s) {
        return new SlabRow(s.getDirection(), s.getLocationKey(), s.getUnit(), s.getFromUnit(), s.getToUnit(),
                s.getFirstUnit(), s.getFirstRate(), s.getAddUnit(), s.getAddRate(), s.getPaymentMode(), s.getShipmentType());
    }

    private static ChargeRule toRule(RateChargeRuleEntity r) {
        return new ChargeRule(r.getChargeCode(), r.getSeq(), r.getCalcType(), r.getRateValue(), r.getFixedAmount(),
                r.getMinAmount(), r.getMaxAmount(), r.getBase(), r.getBaseCodes(), r.getTierFrom(), r.getTierTo(),
                r.getAppliesWhen(), r.getAppliesValue(), r.getDirections(), r.getValidFrom(), r.getValidTo());
    }

    /** Package-private: also used by RateCardQueryService to show rule-based zones on the lane endpoint. */
    static PincodeInfo toPincode(PartnerPincodeEntity p) {
        return new PincodeInfo(p.getId().getPincode(), p.getCityCode(), p.getStateId(), p.isPickup(), p.isDelivery(),
                p.isCod(), p.isPrepaid(), p.isReversePickup(), p.isOda(), p.isSpecialCity());
    }
}
