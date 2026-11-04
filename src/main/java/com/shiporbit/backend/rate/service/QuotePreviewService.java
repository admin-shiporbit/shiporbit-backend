package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.entity.DeliveryPartnerEntity;
import com.shiporbit.backend.rate.core.ChargeableWeight;
import com.shiporbit.backend.rate.core.QuoteRequest;
import com.shiporbit.backend.rate.core.WeightRules;
import com.shiporbit.backend.rate.dto.response.QuotePreview;
import com.shiporbit.backend.rate.dto.response.QuotePreview.ServiceWeight;
import com.shiporbit.backend.rate.entity.RateCardEntity;
import com.shiporbit.backend.rate.entity.RateServiceEntity;
import com.shiporbit.backend.rate.repository.RateCardRepository;
import com.shiporbit.backend.rate.repository.RateServiceRepository;
import com.shiporbit.backend.repository.DeliveryPartnerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Step-3 check: validates/normalises a QuoteRequest and computes chargeable weight
 * against each enabled service's ACTIVE card. Pricing comes in step 4.
 * One card query per service; fine for a preview, the real quote path will use the cache.
 */
@Service
@Transactional(readOnly = true)
public class QuotePreviewService {

    private final RateServiceRepository serviceRepo;
    private final RateCardRepository cardRepo;
    private final DeliveryPartnerRepository partnerRepo;

    public QuotePreviewService(RateServiceRepository serviceRepo, RateCardRepository cardRepo,
                               DeliveryPartnerRepository partnerRepo) {
        this.serviceRepo = serviceRepo;
        this.cardRepo = cardRepo;
        this.partnerRepo = partnerRepo;
    }

    public QuotePreview preview(QuoteRequest request) {
        Map<Integer, String> partnerNames = partnerRepo.findAll().stream()
                .collect(Collectors.toMap(DeliveryPartnerEntity::getId, DeliveryPartnerEntity::getPartnerName));
        PartnerFilter.requireKnown(request.partners(), partnerNames.values());

        List<ServiceWeight> services = serviceRepo.findByEnabledTrue().stream()
                .sorted(Comparator.comparing(RateServiceEntity::getPartnerId)
                        .thenComparing(RateServiceEntity::getServiceCode))
                .filter(s -> request.includesPartner(partnerNames.getOrDefault(s.getPartnerId(), "")))
                .map(s -> weightFor(s, partnerNames.get(s.getPartnerId()), request))
                .toList();

        return new QuotePreview(request, request.shipmentType(), request.totalPieces(), services);
    }

    private ServiceWeight weightFor(RateServiceEntity service, String partnerName, QuoteRequest request) {
        return cardRepo.findActiveFor(service.getId(), request.pickupDate())
                .map(card -> withCard(service, partnerName, card, request))
                .orElseGet(() -> new ServiceWeight(service.getPartnerId(), partnerName, service.getServiceCode(),
                        service.getPricingModel(), null, null, null, null, null, null,
                        "No ACTIVE rate card on " + request.pickupDate()));
    }

    private ServiceWeight withCard(RateServiceEntity service, String partnerName, RateCardEntity card,
                                   QuoteRequest request) {
        WeightRules rules = new WeightRules(card.getVolumetricDivisor(), card.getCftKg(),
                card.getMinChargeableWeightKg(), card.getMpsRule());
        ChargeableWeight w = ChargeableWeight.of(request.packages(), rules);
        boolean within = w.chargeableWeightKg().compareTo(card.getMaxWeightKg()) <= 0;
        return new ServiceWeight(service.getPartnerId(), partnerName, service.getServiceCode(),
                service.getPricingModel(), card.getVersionLabel(), w.deadWeightKg(), w.volumetricWeightKg(),
                w.chargeableWeightKg(), card.getMaxWeightKg(), within,
                within ? null : "Exceeds max weight " + card.getMaxWeightKg() + " kg (WEIGHT_LIMIT_EXCEEDED)");
    }
}
