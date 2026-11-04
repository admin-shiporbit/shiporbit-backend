package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.entity.DeliveryPartnerEntity;
import com.shiporbit.backend.entity.StateEntity;
import com.shiporbit.backend.rate.dto.response.RateCardViews.CardView;
import com.shiporbit.backend.rate.dto.response.RateCardViews.LaneView;
import com.shiporbit.backend.rate.dto.response.RateCardViews.PartnerLaneView;
import com.shiporbit.backend.rate.dto.response.RateCardViews.PincodeView;
import com.shiporbit.backend.rate.dto.response.RateCardViews.RuleView;
import com.shiporbit.backend.rate.dto.response.RateCardViews.ServiceView;
import com.shiporbit.backend.rate.dto.response.RateCardViews.SlabView;
import com.shiporbit.backend.rate.dto.response.RateCardViews.ZoneView;
import com.shiporbit.backend.rate.repository.PartnerPincodeRepository;
import com.shiporbit.backend.rate.repository.RateCardRepository;
import com.shiporbit.backend.rate.repository.RateChargeRuleRepository;
import com.shiporbit.backend.rate.repository.RateServiceRepository;
import com.shiporbit.backend.rate.repository.RateSlabRepository;
import com.shiporbit.backend.rate.repository.ZoneMapRepository;
import com.shiporbit.backend.rate.entity.PartnerPincodeEntity;
import com.shiporbit.backend.rate.entity.ZoneMapEntity;
import com.shiporbit.backend.rate.engine.PincodeInfo;
import com.shiporbit.backend.rate.engine.zone.ZoneConfig;
import com.shiporbit.backend.rate.entity.PricingModel;
import com.shiporbit.backend.repository.DeliveryPartnerRepository;
import com.shiporbit.backend.repository.StateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Read-only access to rate-card tables, used by RateCardController to inspect
 * what the calculator will read. No pricing logic here.
 */
@Service
@Transactional(readOnly = true)
public class RateCardQueryService {

    private final RateServiceRepository serviceRepo;
    private final RateCardRepository cardRepo;
    private final RateSlabRepository slabRepo;
    private final RateChargeRuleRepository ruleRepo;
    private final PartnerPincodeRepository pincodeRepo;
    private final ZoneMapRepository zoneRepo;
    private final DeliveryPartnerRepository partnerRepo;
    private final StateRepository stateRepo;
    private final ZoneConfigLoader zoneConfigLoader;

    public RateCardQueryService(RateServiceRepository serviceRepo, RateCardRepository cardRepo,
                                RateSlabRepository slabRepo, RateChargeRuleRepository ruleRepo,
                                PartnerPincodeRepository pincodeRepo, ZoneMapRepository zoneRepo,
                                DeliveryPartnerRepository partnerRepo, StateRepository stateRepo,
                                ZoneConfigLoader zoneConfigLoader) {
        this.serviceRepo = serviceRepo;
        this.cardRepo = cardRepo;
        this.slabRepo = slabRepo;
        this.ruleRepo = ruleRepo;
        this.pincodeRepo = pincodeRepo;
        this.zoneRepo = zoneRepo;
        this.partnerRepo = partnerRepo;
        this.stateRepo = stateRepo;
        this.zoneConfigLoader = zoneConfigLoader;
    }

    public List<ServiceView> enabledServices() {
        return serviceRepo.findByEnabledTrue().stream().map(ServiceView::from).toList();
    }

    public Optional<CardView> activeCard(Long serviceId, LocalDate date) {
        return cardRepo.findActiveFor(serviceId, date).map(card -> CardView.from(card,
                slabRepo.findByRateCardIdOrderByDirectionAscLocationKeyAscFromUnitAsc(card.getId())
                        .stream().map(SlabView::from).toList(),
                ruleRepo.findByRateCardIdOrderBySeqAsc(card.getId())
                        .stream().map(RuleView::from).toList()));
    }

    public LaneView lane(String origin, String destination) {
        // 7 queries in total: pincodes, zones, partner names (14 rows), state names (only states found),
        // zone rules (3, uncached so this endpoint always shows current DB data)
        List<PartnerPincodeEntity> pincodeRows = pincodeRepo.findByIdPincodeIn(Set.copyOf(List.of(origin, destination))); // copyOf: origin may equal destination
        List<ZoneMapEntity> zoneRows = zoneRepo.findByIdOriginPincodeAndIdDestinationPincode(origin, destination);
        ZoneConfig zoneConfig = zoneConfigLoader.read();
        Map<Integer, String> partnerNames = partnerNames();
        Map<Integer, String> stateNames = stateRepo.findAllById(
                        pincodeRows.stream().map(PartnerPincodeEntity::getStateId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(StateEntity::getId, StateEntity::getName));

        // partnerId -> pincode -> row
        Map<Integer, Map<String, PartnerPincodeEntity>> pinByPartner = pincodeRows.stream()
                .collect(Collectors.groupingBy(p -> p.getId().getPartnerId(),
                        Collectors.toMap(p -> p.getId().getPincode(), p -> p)));
        Map<Integer, List<ZoneView>> zonesByPartner = zoneRows.stream()
                .collect(Collectors.groupingBy(z -> z.getId().getPartnerId(),
                        Collectors.mapping(ZoneView::from, Collectors.toList())));

        Set<Integer> partnerIds = new TreeSet<>(pinByPartner.keySet());
        partnerIds.addAll(zonesByPartner.keySet());

        List<PartnerLaneView> partners = partnerIds.stream().map(id -> {
            Map<String, PartnerPincodeEntity> pins = pinByPartner.getOrDefault(id, Map.of());
            return new PartnerLaneView(id, partnerNames.get(id),
                    toView(pins.get(origin), stateNames),
                    toView(pins.get(destination), stateNames),
                    withRuleZones(id, pins.get(origin), pins.get(destination),
                            zonesByPartner.getOrDefault(id, List.of()), zoneConfig));
        }).toList();
        return new LaneView(origin, destination, partners);
    }

    /**
     * SO_ZONE_MAP zones first; for pricing models they don't cover, the zone the partner's rules give.
     * Same order as RateCardLoader.lanes(), so this shows the zone the calculator will use.
     */
    private static List<ZoneView> withRuleZones(Integer partnerId, PartnerPincodeEntity origin,
                                                PartnerPincodeEntity destination, List<ZoneView> mapped,
                                                ZoneConfig zoneConfig) {
        if (origin == null || destination == null) {
            return mapped;
        }
        List<ZoneView> zones = new ArrayList<>(mapped);
        Set<PricingModel> covered = mapped.stream().map(ZoneView::pricingModel).collect(Collectors.toSet());
        PincodeInfo from = RateCardLoader.toPincode(origin);
        PincodeInfo to = RateCardLoader.toPincode(destination);
        zoneConfig.modelsFor(partnerId).stream().sorted()
                .filter(model -> !covered.contains(model))
                .forEach(model -> zoneConfig.resolve(partnerId, model, from, to)
                        .ifPresent(key -> zones.add(ZoneView.fromRule(model, key))));
        return zones;
    }

    private static PincodeView toView(PartnerPincodeEntity row, Map<Integer, String> stateNames) {
        return row == null ? null : PincodeView.from(row, stateNames.get(row.getStateId()));
    }

    /** Partner id -> name. */
    private Map<Integer, String> partnerNames() {
        return partnerRepo.findAll().stream()
                .collect(Collectors.toMap(DeliveryPartnerEntity::getId, DeliveryPartnerEntity::getPartnerName));
    }
}
