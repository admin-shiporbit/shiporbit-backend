package com.shiporbit.backend.rate.api;

// FLOW START. Imports: request/response DTOs, the service that does the work, Bean Validation and Spring Web annotations.
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
// Spring MVC controller: every method returns JSON (Jackson serialises the return value).
@RestController
// All endpoints in this class live under /api/v1/rates/compare. SecurityConfig marks this path permitAll (public website calculator).
@RequestMapping("/api/v1/rates/compare")
public class RateCompareController {

    // The service that converts, prices and sorts (RateCompareService). Injected by Spring.
    private final RateCompareService compareService;

    // Constructor injection: Spring passes the RateCompareService bean.
    public RateCompareController(RateCompareService compareService) {
        // Keep the reference for the request method below.
        this.compareService = compareService;
    }

    // Handles HTTP POST /api/v1/rates/compare. Before this line runs: TraceFilter puts trace_id (header trace_id / X-Trace-Id, or a new one) into MDC for every log line.
    @PostMapping
    // @RequestBody: Jackson reads the JSON body into CompareRatesRequest. @Valid: Bean Validation runs the regex/size checks on it; a failure throws MethodArgumentNotValidException -> GlobalExceptionHandler returns 400 with ErrorResponse (SO-GEN code). Nothing below runs in that case.
    public CompareRatesResponse compare(@Valid @RequestBody CompareRatesRequest request) {
        // Step 1 -> RateCompareService.compare(): map the request, price every partner, sort, build the response. The returned object is written as the JSON response (HTTP 200).
        return compareService.compare(request);
    }
}
