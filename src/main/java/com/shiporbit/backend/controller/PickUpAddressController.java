package com.shiporbit.backend.controller;

import com.shiporbit.backend.dto.AddressRequest;
import com.shiporbit.backend.dto.AddressResponse;
import com.shiporbit.backend.security.ShipOrbitUserPrincipal;
import com.shiporbit.backend.service.PickupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RequestMapping("/api/v1/pickup")
@RestController
public class PickUpAddressController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PickUpAddressController.class);

    @Autowired
    private PickupService pickupService;

    @GetMapping("/my-address")
    public ResponseEntity<List<AddressResponse>> mySavedAddresses(Authentication auth) {
        ShipOrbitUserPrincipal principal =
                (ShipOrbitUserPrincipal) auth.getPrincipal();

        UUID userId = principal.userId();
        LOGGER.info("Fetching pickup addresses for user {}", userId);
        return ResponseEntity.ok(pickupService.myAddresses(userId));
    }

    @PostMapping("/addresses")
    public ResponseEntity<AddressResponse> addNewAddress(
            @Valid @RequestBody AddressRequest request,
            Authentication authentication
    ) {
        ShipOrbitUserPrincipal principal =
                (ShipOrbitUserPrincipal) authentication.getPrincipal();

        AddressResponse response = pickupService.addNewAddress(
                principal.userId(),
                request
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/addresses/{addressId}")
    public ResponseEntity<AddressResponse> updateAddress(
            @PathVariable UUID addressId,
            @Valid @RequestBody AddressRequest request,
            Authentication authentication
    ) {
        ShipOrbitUserPrincipal principal =
                (ShipOrbitUserPrincipal) authentication.getPrincipal();

        return ResponseEntity.ok(pickupService.updateAddress(
                principal.userId(),
                addressId,
                request
        ));
    }

    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<Void> deleteAddress(
            @PathVariable UUID addressId,
            Authentication authentication
    ) {
        ShipOrbitUserPrincipal principal =
                (ShipOrbitUserPrincipal) authentication.getPrincipal();
        pickupService.deleteAddress(principal.userId(), addressId);
        return ResponseEntity.noContent().build();
    }
}
