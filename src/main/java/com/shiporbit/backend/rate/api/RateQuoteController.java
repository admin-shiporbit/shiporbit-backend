package com.shiporbit.backend.rate.api;

import com.shiporbit.backend.rate.core.QuoteRequest;
import com.shiporbit.backend.rate.dto.response.QuotePreview;
import com.shiporbit.backend.rate.dto.response.QuoteResponse;
import com.shiporbit.backend.rate.service.QuotePreviewService;
import com.shiporbit.backend.rate.service.RateQuoteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rate-card quotes.
 * POST /api/v1/rates/quotes          : price every enabled rate-card service (or the partners filter).
 * POST /api/v1/rates/quotes/preview  : validation + chargeable weight only.
 */
@RestController
@RequestMapping("/api/v1/rates/quotes")
public class RateQuoteController {

    private final RateQuoteService quoteService;
    private final QuotePreviewService previewService;

    public RateQuoteController(RateQuoteService quoteService, QuotePreviewService previewService) {
        this.quoteService = quoteService;
        this.previewService = previewService;
    }

    @PostMapping
    public QuoteResponse quote(@Valid @RequestBody QuoteRequest request) {
        return quoteService.quote(request);
    }

    @PostMapping("/preview")
    public QuotePreview preview(@Valid @RequestBody QuoteRequest request) {
        return previewService.preview(request);
    }
}
