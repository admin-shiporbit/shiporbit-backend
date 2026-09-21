package com.shiporbit.backend.wallet.service;

import com.shiporbit.backend.entity.Users;
import com.shiporbit.backend.wallet.entity.WalletEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

public interface WalletService {
    public WalletEntity getOrCreateWallet(UUID user);
    public WalletEntity creditWallet(UUID userId, BigDecimal amount, String type, String referenceId, String gateway, String transactionId);
    public WalletEntity debitWallet(UUID userId, BigDecimal amount, String type, String referenceId);
}
