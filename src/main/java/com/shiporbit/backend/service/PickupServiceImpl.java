package com.shiporbit.backend.service;

import com.shiporbit.backend.dto.AddressRequest;
import com.shiporbit.backend.dto.AddressResponse;
import com.shiporbit.backend.entity.PickupEntity;
import com.shiporbit.backend.entity.StateEntity;
import com.shiporbit.backend.entity.Users;
import com.shiporbit.backend.exception.AddressNotFoundException;
import com.shiporbit.backend.repository.PickupRepository;
import com.shiporbit.backend.repository.StateRepository;
import com.shiporbit.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Comparator;

@Service
public class PickupServiceImpl implements PickupService {

    private final PickupRepository pickupRepository;
    private final UserRepository userRepository;
    private final StateRepository stateRepository;

    public PickupServiceImpl(
            PickupRepository pickupRepository,
            UserRepository userRepository,
            StateRepository stateRepository
    ) {
        this.pickupRepository = pickupRepository;
        this.userRepository = userRepository;
        this.stateRepository = stateRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> myAddresses(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("Authenticated user not found");
        }

        return pickupRepository.findAllByUser_Id(userId).stream()
                .sorted(Comparator
                        .comparing(PickupEntity::isDefault).reversed()
                        .thenComparing(PickupEntity::getCreatedAt, Comparator.reverseOrder()))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public AddressResponse addNewAddress(UUID userId, AddressRequest request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));

        StateEntity state = resolveState(request.stateCode());

        List<PickupEntity> existingAddresses = pickupRepository.findAllByUser_Id(userId);
        boolean makeDefault = request.isDefault() || existingAddresses.isEmpty();

        if (makeDefault) {
            existingAddresses.stream()
                    .filter(PickupEntity::isDefault)
                    .forEach(address -> address.setDefault(false));
            pickupRepository.saveAll(existingAddresses);
        }

        PickupEntity address = new PickupEntity();
        address.setUser(user);
        applyRequest(address, request, state);
        address.setDefault(makeDefault);

        return toResponse(pickupRepository.save(address));
    }

    @Override
    @Transactional
    public AddressResponse updateAddress(
            UUID userId,
            UUID addressId,
            AddressRequest request
    ) {
        PickupEntity address = findOwnedAddress(userId, addressId);
        StateEntity state = resolveState(request.stateCode());

        if (request.isDefault()) {
            clearOtherDefaults(userId, addressId);
            address.setDefault(true);
        } else  {
            address.setDefault(false);
        }

        applyRequest(address, request, state);
        return toResponse(pickupRepository.save(address));
    }

    @Override
    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        PickupEntity address = findOwnedAddress(userId, addressId);
        boolean deletedDefault = address.isDefault();
        pickupRepository.delete(address);
        pickupRepository.flush();

        if (deletedDefault) {
            pickupRepository.findAllByUser_Id(userId).stream()
                    .max(Comparator.comparing(PickupEntity::getCreatedAt))
                    .ifPresent(nextDefault -> {
                        nextDefault.setDefault(true);
                        pickupRepository.save(nextDefault);
                    });
        }
    }

    private PickupEntity findOwnedAddress(UUID userId, UUID addressId) {
        return pickupRepository.findByIdAndUser_Id(addressId, userId)
                .orElseThrow(() -> new AddressNotFoundException(
                        "Pickup address not found: " + addressId
                ));
    }

    private StateEntity resolveState(String requestedStateCode) {
        String stateCode = requestedStateCode.trim().toUpperCase(Locale.ROOT);
        return stateRepository.findByCodeIgnoreCase(stateCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid state code: " + stateCode
                ));
    }

    private void clearOtherDefaults(UUID userId, UUID excludedAddressId) {
        List<PickupEntity> addresses = pickupRepository.findAllByUser_Id(userId);
        addresses.stream()
                .filter(PickupEntity::isDefault)
                .filter(item -> !item.getId().equals(excludedAddressId))
                .forEach(item -> item.setDefault(false));
        pickupRepository.saveAll(addresses);
    }

    private void applyRequest(
            PickupEntity address,
            AddressRequest request,
            StateEntity state
    ) {
        address.setLabel(request.label().trim());
        address.setIsdCode(request.isdCode().trim());
        address.setPhoneNumber(request.phoneNumber().trim());
        address.setAddressLine1(request.addressLine1().trim());
        address.setAddressLine2(normalizeOptional(request.addressLine2()));
        address.setCity(request.city().trim());
        address.setState(state);
        address.setPinCode(request.pinCode().trim());
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private AddressResponse toResponse(PickupEntity address) {
        return new AddressResponse(
                address.getId(),
                address.getLabel(),
                address.getUser().getId(),
                address.getIsdCode(),
                address.getPhoneNumber(),
                address.getAddressLine1(),
                address.getAddressLine2(),
                address.getCity(),
                address.getState().getId(),
                address.getState().getCode(),
                address.getState().getName(),
                address.getPinCode(),
                address.isDefault(),
                address.getCreatedAt(),
                address.getUpdatedAt()
        );
    }
}
