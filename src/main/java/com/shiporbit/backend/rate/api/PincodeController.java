package com.shiporbit.backend.rate.api;

import com.shiporbit.backend.rate.dto.response.PincodeLookupResponse;
import com.shiporbit.backend.rate.service.PincodeLookupService;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/v1/pincodes/{pincode}: location + partner coverage from ShipOrbit's pincode list.
 * Public (website pincode fields, called through the UI's /api/pincode route).
 * 200 found, 400 not a 6-digit Indian pincode (SO-GEN-001), 404 not in the list (SO-SVC-008).
 */
@RestController
@RequestMapping("/api/v1/pincodes")
@Validated
public class PincodeController {

    private final PincodeLookupService lookupService;

    public PincodeController(PincodeLookupService lookupService) {
        this.lookupService = lookupService;
    }

    @GetMapping("/{pincode}")
    public PincodeLookupResponse lookup(
            @PathVariable @Pattern(regexp = "[1-9]\\d{5}", message = "must be a 6-digit Indian pincode") String pincode) {
        return lookupService.lookup(pincode);
    }
}
