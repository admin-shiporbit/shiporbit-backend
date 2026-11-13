package com.shiporbit.backend.rate.service;

// Imports: JPA entities + repositories, engine records, cache config, zone config loader.
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
// Spring service bean.
@Service
// Every public method runs in a read-only DB transaction (no dirty checking, no flush).
@Transactional(readOnly = true)
// Turns rate tables into immutable, cacheable engine records.
public class RateCardLoader {

    // Logger.
    private static final Logger LOGGER = LoggerFactory.getLogger(RateCardLoader.class);

    // SO_RATE_SERVICE.
    private final RateServiceRepository serviceRepo;
    // SO_RATE_CARD.
    private final RateCardRepository cardRepo;
    // SO_RATE_SLAB.
    private final RateSlabRepository slabRepo;
    // SO_RATE_CHARGE_RULE.
    private final RateChargeRuleRepository ruleRepo;
    // SO_PARTNER_PINCODE.
    private final PartnerPincodeRepository pincodeRepo;
    // SO_ZONE_MAP (exact pincode-pair overrides).
    private final ZoneMapRepository zoneRepo;
    // Cached partner id -> name.
    private final PartnerDirectory partnerDirectory;
    // Cached zone groups / rules / pairs (SO_ZONE_GROUP, SO_ZONE_RULE, SO_ZONE_PAIR).
    private final ZoneConfigLoader zoneConfigLoader;

    // Constructor injection.
    public RateCardLoader(RateServiceRepository serviceRepo, RateCardRepository cardRepo, RateSlabRepository slabRepo,
                          RateChargeRuleRepository ruleRepo, PartnerPincodeRepository pincodeRepo,
                          ZoneMapRepository zoneRepo, PartnerDirectory partnerDirectory,
                          ZoneConfigLoader zoneConfigLoader) {
        // Store.
        this.serviceRepo = serviceRepo;
        // Store.
        this.cardRepo = cardRepo;
        // Store.
        this.slabRepo = slabRepo;
        // Store.
        this.ruleRepo = ruleRepo;
        // Store.
        this.pincodeRepo = pincodeRepo;
        // Store.
        this.zoneRepo = zoneRepo;
        // Store.
        this.partnerDirectory = partnerDirectory;
        // Store.
        this.zoneConfigLoader = zoneConfigLoader;
    }

    /** One enabled service and its ACTIVE card on the date (card null = none active). */
    // One service + its active card (null when none).
    public record LoadedService(ServiceInfo service, RateCardSnapshot card) { }

    /** Every enabled service with its ACTIVE card on the date, sorted by partner id, service code. Cached per date. */
    // Spring Cache (Caffeine, RateCacheConfig.SERVICES): result cached per date; the body runs only on a cache miss.
    @Cacheable(RateCacheConfig.SERVICES)
    // Used in Step 2 (RateCompareService) and Step 3b (RateQuoteService) - the second call is a cache hit.
    public List<LoadedService> servicesOn(LocalDate date) {
        // Logged only on a cache miss: if this line is absent for a quote, cards came from the cache.
        // Debug log (cache miss only).
        LOGGER.debug("Rate-card cache MISS: loading services and ACTIVE cards for {} from DB", date);
        // Partner names (separate bean, so its own cache applies).
        Map<Integer, String> partnerNames = partnerDirectory.names();   // other bean -> goes through the cache
        // Query 1: ACTIVE cards valid on the date (EFFECTIVE_FROM <= date <= EFFECTIVE_TO or open),
        Map<Long, RateCardEntity> cardByService = cardRepo.findAllActiveOn(date).stream()
                // keyed by service id.
                .collect(Collectors.toMap(RateCardEntity::getServiceId, Function.identity()));

        // Ids of those cards.
        Set<Long> cardIds = cardByService.values().stream().map(RateCardEntity::getId).collect(Collectors.toSet());
        // Query 2: all slabs of those cards (one IN query),
        Map<Long, List<RateSlabEntity>> slabsByCard = cardIds.isEmpty() ? Map.of()
                // grouped by card id.
                : slabRepo.findByRateCardIdIn(cardIds).stream().collect(Collectors.groupingBy(RateSlabEntity::getRateCardId));
        // Query 3: all charge rules of those cards,
        Map<Long, List<RateChargeRuleEntity>> rulesByCard = cardIds.isEmpty() ? Map.of()
                // grouped by card id.
                : ruleRepo.findByRateCardIdIn(cardIds).stream().collect(Collectors.groupingBy(RateChargeRuleEntity::getRateCardId));

        // Query 4: enabled services,
        return serviceRepo.findByEnabledTrue().stream()
                // sorted by partner id, then service code,
                .sorted(Comparator.comparing(RateServiceEntity::getPartnerId).thenComparing(RateServiceEntity::getServiceCode))
                // each mapped to:
                .map(s -> {
                    // ServiceInfo (service id, partner id, partner name,
                    ServiceInfo info = new ServiceInfo(s.getId(), s.getPartnerId(), partnerNames.get(s.getPartnerId()),
                            // service code, pricing model, mode),
                            s.getServiceCode(), s.getPricingModel(), s.getMode());
                    // its card, if any,
                    RateCardEntity card = cardByService.get(s.getId());
                    // LoadedService(info, snapshot or null) where snapshot copies the card
                    return new LoadedService(info, card == null ? null : snapshot(info, card,
                            // with its slabs
                            slabsByCard.getOrDefault(card.getId(), List.of()),
                            // and rules into immutable records.
                            rulesByCard.getOrDefault(card.getId(), List.of())));
                })
                // Immutable list (safe to share from cache).
                .toList();
    }

    /** SO_DELIVERY_PARTNER_LIST id -> name. Delegates to the cached PartnerDirectory. */
    // partnerId -> name.
    public Map<Integer, String> partnerNames() {
        // From PartnerDirectory (cached).
        return partnerDirectory.names();     // cached in PartnerDirectory
    }

    /**
     * Pincode flags and zone keys for origin -> destination, per partner. Two queries. Cached per lane.
     * Zone key per pricing model: the SO_ZONE_MAP row for the exact pincodes if there is one (override),
     * otherwise the partner's zone rules (SO_ZONE_RULE), worked out from both pincodes' city and state.
     */
    // Spring Cache (RateCacheConfig.LANES): cached per (origin, destination). Step 3c.
    @Cacheable(RateCacheConfig.LANES)
    // Returns partnerId -> lane for this pincode pair.
    public Map<Integer, PartnerLane> lanes(String origin, String destination) {
        // Debug log (cache miss only).
        LOGGER.debug("Rate-card cache MISS: loading lane {} -> {} from DB", origin, destination);
        // partnerId -> (pincode -> flags).
        Map<Integer, Map<String, PincodeInfo>> pins = new HashMap<>();
        // Query 1: SO_PARTNER_PINCODE rows for both pincodes, all partners.
        for (PartnerPincodeEntity p : pincodeRepo.findByIdPincodeIn(Set.copyOf(List.of(origin, destination)))) {
            // Group by partner, then pincode -> PincodeInfo (pickup, delivery, COD, prepaid, ODA, special city, city code, state id).
            pins.computeIfAbsent(p.getId().getPartnerId(), k -> new HashMap<>()).put(p.getId().getPincode(), toPincode(p));
        }
        // partnerId -> (pricing model -> location key) from exact overrides.
        Map<Integer, Map<PricingModel, String>> keys = new HashMap<>();
        // Query 2: SO_ZONE_MAP rows for this exact pincode pair.
        for (ZoneMapEntity z : zoneRepo.findByIdOriginPincodeAndIdDestinationPincode(origin, destination)) {
            // Group by partner,
            keys.computeIfAbsent(z.getId().getPartnerId(), k -> new EnumMap<>(PricingModel.class))
                    // model -> location key (zone / band / pair).
                    .put(z.getId().getPricingModel(), z.getLocationKey());
        }

        // Zone rules for every partner (cached once, ZoneConfigLoader.current()).
        ZoneConfig zoneConfig = zoneConfigLoader.current();    // other bean -> goes through the cache

        // Result map.
        Map<Integer, PartnerLane> lanes = new HashMap<>();
        // Every partner that has a pincode row
        Set<Integer> partnerIds = new HashSet<>(pins.keySet());
        // or an override.
        partnerIds.addAll(keys.keySet());
        // For each such partner:
        for (Integer id : partnerIds) {
            // its pincode rows,
            Map<String, PincodeInfo> p = pins.getOrDefault(id, Map.of());
            // origin row (null = partner does not serve origin),
            PincodeInfo from = p.get(origin);
            // destination row (null = does not serve destination).
            PincodeInfo to = p.get(destination);
            // Not new EnumMap<>(Map.of()): that constructor throws on an empty non-EnumMap.
            // Location keys for this partner,
            Map<PricingModel, String> partnerKeys = new EnumMap<>(PricingModel.class);
            // starting with the SO_ZONE_MAP overrides.
            partnerKeys.putAll(keys.getOrDefault(id, Map.of()));
            // Zone rules need both pincodes.
            if (from != null && to != null) {          // rules need both pincodes; a missing one is NOT_SERVICEABLE anyway
                // For every pricing model that has zone rules for this partner:
                for (PricingModel model : zoneConfig.modelsFor(id)) {
                    // no override for this model?
                    if (!partnerKeys.containsKey(model)) {      // SO_ZONE_MAP wins
                        // Work out the key from the zone rules (city / state groups; e.g. XpressBees "W1-N1") and add it when a rule matches.
                        zoneConfig.resolve(id, model, from, to).ifPresent(key -> partnerKeys.put(model, key));
                    }
                }
            }
            // Lane = origin, destination, keys.
            lanes.put(id, new PartnerLane(from, to, partnerKeys));
        }
        // Immutable copy (cached).
        return Map.copyOf(lanes);
    }

    // ---- entity -> engine record mapping ----

    // Card entity -> RateCardSnapshot:
    private static RateCardSnapshot snapshot(ServiceInfo info, RateCardEntity c,
                                             List<RateSlabEntity> slabs, List<RateChargeRuleEntity> rules) {
        // service, card id, version, SAMPLE/CONTRACT,
        return new RateCardSnapshot(info, c.getId(), c.getVersionLabel(), c.getDataStatus(),
                // weight rules (volumetric divisor, CFT kg, minimum chargeable kg, MPS rule),
                new WeightRules(c.getVolumetricDivisor(), c.getCftKg(), c.getMinChargeableWeightKg(), c.getMpsRule()),
                // max weight, max piece weight, max km, RTO rule, RTO %, minimum bill,
                c.getMaxWeightKg(), c.getMaxPieceWeightKg(), c.getMaxDistanceKm(), c.getRtoRule(), c.getRtoPercent(), c.getMinBill(),
                // rates include GST?, rounding (CEIL_STEP / EXACT),
                c.isRatesIncludeGst(), c.getRounding(),
                // slabs,
                slabs.stream().map(RateCardLoader::toSlab).toList(),
                // rules (sorted by SEQ in the record's constructor).
                rules.stream().map(RateCardLoader::toRule).toList());
    }

    // Slab entity -> SlabRow.
    private static SlabRow toSlab(RateSlabEntity s) {
        // Direction, location key, unit, from/to range,
        return new SlabRow(s.getDirection(), s.getLocationKey(), s.getUnit(), s.getFromUnit(), s.getToUnit(),
                // first unit + rate, additional unit + rate, payment mode, shipment type.
                s.getFirstUnit(), s.getFirstRate(), s.getAddUnit(), s.getAddRate(), s.getPaymentMode(), s.getShipmentType());
    }

    // Rule entity -> ChargeRule.
    private static ChargeRule toRule(RateChargeRuleEntity r) {
        // Code, seq, calc type, rate, fixed amount,
        return new ChargeRule(r.getChargeCode(), r.getSeq(), r.getCalcType(), r.getRateValue(), r.getFixedAmount(),
                // min, max, base, base codes, tier range,
                r.getMinAmount(), r.getMaxAmount(), r.getBase(), r.getBaseCodes(), r.getTierFrom(), r.getTierTo(),
                // condition, condition value, directions, validity.
                r.getAppliesWhen(), r.getAppliesValue(), r.getDirections(), r.getValidFrom(), r.getValidTo());
    }

    /** Package-private: also used by RateCardQueryService to show rule-based zones on the lane endpoint. */
    // Pincode entity -> PincodeInfo.
    static PincodeInfo toPincode(PartnerPincodeEntity p) {
        // Pincode, city code, state id, pickup, delivery,
        return new PincodeInfo(p.getId().getPincode(), p.getCityCode(), p.getStateId(), p.isPickup(), p.isDelivery(),
                // COD, prepaid, reverse pickup, ODA, special city.
                p.isCod(), p.isPrepaid(), p.isReversePickup(), p.isOda(), p.isSpecialCity());
    }
}
