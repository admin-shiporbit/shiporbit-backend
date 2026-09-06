package com.shiporbit.backend.controller;

import com.shiporbit.backend.dto.DeliveryPartnerResponse;
import com.shiporbit.backend.dto.RateQuoteRequest;
import com.shiporbit.backend.dto.RateQuoteResponse;
import com.shiporbit.backend.service.RateService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RequestMapping("/api/v1/rates")
@RestController
public class RateController {

    private final RateService rateService;

    public RateController(RateService rateService) {
        this.rateService = rateService;
    }

    @GetMapping("/partners")
    public ResponseEntity<List<DeliveryPartnerResponse>> partners() {
        return ResponseEntity.ok(rateService.availablePartners());
    }

    @PostMapping("/quote/{partnerCode}")
    public ResponseEntity<RateQuoteResponse> quote(
            @PathVariable String partnerCode,
            @Valid @RequestBody RateQuoteRequest request
    ) {
        return ResponseEntity.ok(rateService.quote(partnerCode.trim().toUpperCase(Locale.ROOT), request));
    }

    @PostMapping("/quotes")
    public ResponseEntity<List<RateQuoteResponse>> compareAll(@Valid @RequestBody RateQuoteRequest request) {
        return ResponseEntity.ok(rateService.compareAll(request));
    }
}
