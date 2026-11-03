package com.shiporbit.backend.service;

import com.shiporbit.backend.dto.AddressRequest;
import com.shiporbit.backend.dto.AddressResponse;

import java.util.List;
import java.util.UUID;

public interface PickupService {

    List<AddressResponse> myAddresses(UUID userId);

    AddressResponse addNewAddress(UUID userId, AddressRequest addressRequest);

    AddressResponse updateAddress(UUID userId, UUID addressId, AddressRequest addressRequest);

    void deleteAddress(UUID userId, UUID addressId);
}
