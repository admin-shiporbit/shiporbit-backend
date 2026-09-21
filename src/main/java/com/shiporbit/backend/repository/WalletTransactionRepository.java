package com.shiporbit.backend.repository;

//import com.shiporbit.backend.entity.WalletTransactionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletTransactionRepository/* extends JpaRepository<WalletTransactionEntity, UUID>*/ {
//
//    Optional<WalletTransactionEntity> findByReferenceId(String referenceId);
//
//    boolean existsByGatewayTxnId(String gatewayTxnId);
//
//    Page<WalletTransactionEntity> findByWallet_IdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);
}
