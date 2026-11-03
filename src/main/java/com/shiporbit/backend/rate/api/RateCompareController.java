package com.shiporbit.backend.rate.api;

import com.shiporbit.backend.rate.dto.request.CompareRatesRequest;
import com.shiporbit.backend.rate.dto.response.CompareRatesResponse;
import com.shiporbit.backend.rate.service.RateCompareService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rate comparison in the frontend's format (sample_json/rate_calculator).
 * POST /api/v1/rates/compare : same prices as /api/v1/rates/quotes, flat list sorted by fare.
 */
@RestController
@RequestMapping("/api/v1/rates/compare")
public class RateCompareController {

    private final RateCompareService compareService;

    public RateCompareController(RateCompareService compareService) {
        this.compareService = compareService;
    }

    @PostMapping
    public CompareRatesResponse compare(@Valid @RequestBody CompareRatesRequest request) {
        return compareService.compare(request);
    }
}
