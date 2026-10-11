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
import com.shiporbit.backend.repository.DeliveryPartnerRepository;
import com.shiporbit.backend.repository.StateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

    public RateCardQueryService(RateServiceRepository serviceRepo, RateCardRepository cardRepo,
                                RateSlabRepository slabRepo, RateChargeRuleRepository ruleRepo,
                                PartnerPincodeRepository pincodeRepo, ZoneMapRepository zoneRepo,
                                DeliveryPartnerRepository partnerRepo, StateRepository stateRepo) {
        this.serviceRepo = serviceRepo;
        this.cardRepo = cardRepo;
        this.slabRepo = slabRepo;
        this.ruleRepo = ruleRepo;
        this.pincodeRepo = pincodeRepo;
        this.zoneRepo = zoneRepo;
        this.partnerRepo = partnerRepo;
        this.stateRepo = stateRepo;
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
        // 4 queries in total: pincodes, zones, partner names (14 rows), state names (only states found)
        List<PartnerPincodeEntity> pincodeRows = pincodeRepo.findByIdPincodeIn(Set.copyOf(List.of(origin, destination))); // copyOf: origin may equal destination
        List<ZoneMapEntity> zoneRows = zoneRepo.findByIdOriginPincodeAndIdDestinationPincode(origin, destination);
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
                    zonesByPartner.getOrDefault(id, List.of()));
        }).toList();
        return new LaneView(origin, destination, partners);
    }

    private static PincodeView toView(PartnerPincodeEntity row, Map<Integer, String> stateNames) {
        return row == null ? null : PincodeView.from(row, stateNames.get(row.getStateId()));
    }

    /** DeliveryPartnerEntity.id is Long in Java but INT in the DB; key by Integer to match partnerId. */
    private Map<Integer, String> partnerNames() {
        return partnerRepo.findAll().stream()
                .collect(Collectors.toMap(p -> p.getId().intValue(), DeliveryPartnerEntity::getPartnerName));
    }
}
