package com.shiporbit.backend.wallet.service;

import com.shiporbit.backend.entity.Users;
import com.shiporbit.backend.repository.UserRepository;
import com.shiporbit.backend.wallet.repo.WalletRepository;
import com.shiporbit.backend.wallet.entity.WalletEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class WalletServiceImpl implements WalletService {

    private WalletRepository walletRepository;
    private UserRepository userRepository;

    @Autowired
    public WalletServiceImpl(WalletRepository walletRepository, UserRepository userRepository) {
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
    }

    @Override
    public WalletEntity getOrCreateWallet(UUID userId) {

        Optional<WalletEntity> entity = walletRepository.findByUser_Id(userId);
        return entity.orElseGet(()->walletRepository.save(new WalletEntity(null, BigDecimal.ZERO, 0, LocalDateTime.now(), LocalDateTime.now(), userRepository.getReferenceById(userId))));
    }

    @Override
    public WalletEntity creditWallet(UUID userId, BigDecimal amount, String type, String referenceId, String gateway, String transactionId) {
        return null;
    }

    @Override
    public WalletEntity debitWallet(UUID userId, BigDecimal amount, String type, String referenceId) {
        return null;
    }
}
