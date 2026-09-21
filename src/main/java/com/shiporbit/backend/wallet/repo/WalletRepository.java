package com.shiporbit.backend.wallet.repo;

import com.shiporbit.backend.wallet.entity.WalletEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<WalletEntity, UUID>{

    Optional<WalletEntity> findByUser_Id(UUID userId);
}
