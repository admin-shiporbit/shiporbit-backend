package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.entity.StateEntity;
import com.shiporbit.backend.exception.PincodeNotFoundException;
import com.shiporbit.backend.logging.Flow;
import com.shiporbit.backend.rate.cache.PartnerDirectory;
import com.shiporbit.backend.rate.dto.response.PincodeLookupResponse;
import com.shiporbit.backend.rate.dto.response.PincodeLookupResponse.PartnerCoverage;
import com.shiporbit.backend.rate.entity.PartnerPincodeEntity;
import com.shiporbit.backend.rate.provider.LivePartnerQuoteProvider;
import com.shiporbit.backend.rate.repository.PartnerPincodeRepository;
import com.shiporbit.backend.rate.routing.PartnerAllowlistProperties;
import com.shiporbit.backend.rate.routing.PartnerProfileProperties;
import com.shiporbit.backend.repository.StateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Single-pincode lookup from ShipOrbit's pincode list (SO_PARTNER_PINCODE), for the website's pincode fields.
 * <ul>
 *   <li>Live-API partners' rows are ignored: they are sample seed rows, and those partners are checked per
 *       route by their own APIs at quote time.</li>
 *   <li>Location = the CITY_CODE + STATE_ID most enabled partners agree on (ties: lowest partner id), so one
 *       partner's odd row does not decide it; all partners' rows only when no enabled partner has the pincode.</li>
 *   <li>Coverage = enabled partners only (shiporbit.partners.enabled / disabled). A pincode known only to
 *       disabled partners is still found (location shown) but serviceable = false.</li>
 * </ul>
 * One indexed query (SO_IDX_PARTNER_PINCODE_PIN) + one by-id state read.
 */
@Service
@Transactional(readOnly = true)
public class PincodeLookupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PincodeLookupService.class);
    private static final Map<String, String> CLUSTER_NAMES = Map.of("DELHI_NCR", "Delhi NCR");

    private final PartnerPincodeRepository pincodeRepo;
    private final StateRepository stateRepo;
    private final PartnerDirectory partnerDirectory;
    private final PartnerAllowlistProperties allowlist;
    private final PartnerProfileProperties profiles;
    private final LivePartnerQuoteProvider liveProvider;

    public PincodeLookupService(PartnerPincodeRepository pincodeRepo, StateRepository stateRepo,
                                PartnerDirectory partnerDirectory, PartnerAllowlistProperties allowlist,
                                PartnerProfileProperties profiles, LivePartnerQuoteProvider liveProvider) {
        this.pincodeRepo = pincodeRepo;
        this.stateRepo = stateRepo;
        this.partnerDirectory = partnerDirectory;
        this.allowlist = allowlist;
        this.profiles = profiles;
        this.liveProvider = liveProvider;
    }

    public PincodeLookupResponse lookup(String pincode) {
        Set<Integer> livePartners = liveProvider.livePartnerIds();
        List<PartnerPincodeEntity> rows = pincodeRepo.findByIdPincode(pincode).stream()
                .filter(r -> !livePartners.contains(r.getId().getPartnerId()))
                .sorted(Comparator.comparing(r -> r.getId().getPartnerId()))
                .toList();
        if (rows.isEmpty()) {
            Flow.step(LOGGER, "Pincode {}: not in the pincode list", pincode);
            throw new PincodeNotFoundException("Pincode " + pincode + " is not in our pincode list");
        }

        Map<Integer, String> names = partnerDirectory.names();
        // Enabled partners' rows decide the location; disabled partners (several hold only sample seed rows) are a fallback.
        List<PartnerPincodeEntity> enabledRows = rows.stream()
                .filter(r -> allowlist.allows(names.get(r.getId().getPartnerId())))
                .toList();
        PartnerPincodeEntity location = mostCommonLocation(enabledRows.isEmpty() ? rows : enabledRows);
        StateEntity state = stateRepo.findById(location.getStateId()).orElse(null);

        List<PartnerCoverage> partners = enabledRows.stream()
                .map(r -> new PartnerCoverage(displayName(r.getId().getPartnerId(), names), r.isPickup(), r.isDelivery(),
                        r.isCod(), r.isPrepaid(), r.isOda()))
                .sorted(Comparator.comparing(PartnerCoverage::name))
                .toList();
        List<String> checkedPerRoute = livePartners.stream()
                .filter(id -> allowlist.allows(names.get(id)))
                .map(id -> displayName(id, names))
                .sorted()
                .toList();
        boolean serviceable = partners.stream().anyMatch(p -> p.pickup() || p.delivery());

        Flow.step(LOGGER, "Pincode {}: {} / {}, {} enabled partner(s) in list, serviceable {}", pincode,
                location.getCityCode(), state == null ? location.getStateId() : state.getCode(), partners.size(), serviceable);
        return new PincodeLookupResponse(pincode, districtName(location.getCityCode()),
                state == null ? null : state.getName(), state == null ? null : state.getCode(),
                serviceable, partners, checkedPerRoute);
    }

    private String displayName(Integer partnerId, Map<Integer, String> names) {
        return profiles.forPartner(partnerId, names.get(partnerId)).displayName();
    }

    /** Row whose (CITY_CODE, STATE_ID) appears most often; ties go to the lowest partner id (rows are sorted). */
    static PartnerPincodeEntity mostCommonLocation(List<PartnerPincodeEntity> rowsByPartnerId) {
        Map<String, Long> counts = rowsByPartnerId.stream()
                .collect(Collectors.groupingBy(PincodeLookupService::locationKey, Collectors.counting()));
        long best = counts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        return rowsByPartnerId.stream().filter(r -> counts.get(locationKey(r)) == best).findFirst()
                .orElseThrow();
    }

    private static String locationKey(PartnerPincodeEntity r) {
        return r.getCityCode() + "|" + r.getStateId();
    }

    /** CITY_CODE -> display name: "24_PARAGANAS_NORTH" -> "24 Paraganas North", "DELHI_NCR" -> "Delhi NCR". */
    static String districtName(String cityCode) {
        if (cityCode == null || cityCode.isBlank()) {
            return null;
        }
        String code = cityCode.trim().toUpperCase(Locale.ROOT);
        String known = CLUSTER_NAMES.get(code);
        if (known != null) {
            return known;
        }
        return java.util.Arrays.stream(code.split("_+"))
                .filter(w -> !w.isEmpty())
                .map(w -> w.charAt(0) + w.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }
}
