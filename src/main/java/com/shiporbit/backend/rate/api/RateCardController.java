package com.shiporbit.backend.rate.api;

import com.shiporbit.backend.rate.dto.response.RateCardViews.CardView;
import com.shiporbit.backend.rate.dto.response.RateCardViews.LaneView;
import com.shiporbit.backend.rate.dto.response.RateCardViews.ServiceView;
import com.shiporbit.backend.rate.cache.RateCardCacheService;
import com.shiporbit.backend.rate.service.RateCardQueryService;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only inspection of rate-card data (services, active card with slabs/rules,
 * pincode + zone data for a lane). Verifies the JPA mappings end to end.
 */
@RestController
@RequestMapping("/api/v1/rate-cards")
@Validated
public class RateCardController {

    private final RateCardQueryService queryService;
    private final RateCardCacheService cacheService;

    public RateCardController(RateCardQueryService queryService, RateCardCacheService cacheService) {
        this.queryService = queryService;
        this.cacheService = cacheService;
    }

    /** Clears the rate-card caches so DB changes apply to the next quote. ADMIN only (SecurityConfig). */
    @PostMapping("/cache/evict")
    public ResponseEntity<Void> evictCache() {
        cacheService.evictAll();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/services")
    public List<ServiceView> services() {
        return queryService.enabledServices();
    }

    @GetMapping("/services/{serviceId}")
    public ResponseEntity<CardView> activeCard(
            @PathVariable Long serviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return queryService.activeCard(serviceId, date != null ? date : LocalDate.now())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/lane")
    public LaneView lane(@RequestParam @Pattern(regexp = "\\d{6}") String origin,
                         @RequestParam @Pattern(regexp = "\\d{6}") String destination) {
        return queryService.lane(origin, destination);
    }
}
