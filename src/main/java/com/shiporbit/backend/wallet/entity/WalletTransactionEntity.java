package com.shiporbit.backend.wallet.entity;

import com.shiporbit.backend.entity.WalletGateway;
import com.shiporbit.backend.entity.WalletTransactionStatus;
import com.shiporbit.backend.entity.WalletTransactionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name="SO_WALLET_TRANSACTION")
@Getter
@Setter
@NoArgsConstructor
public class WalletTransactionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;


    @ManyToOne
    @JoinColumn(name = "wallet_id",
    nullable = false,
    foreignKey = @ForeignKey(name="SO_FK_WALLET_TXN_WALLET_ID"))
    private WalletEntity walletEntity;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private WalletTransactionStatus status;

    @Column(name = "gateway_txn_id", unique = true, length = 100)
    private String gatewayTxnId;

    @Column(name = "gateway", length = 20)
    @Enumerated(EnumType.STRING)
    private WalletGateway gateway;

    @Column(name = "balance_after", precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "type", length = 20, nullable = false,updatable = false)
    @Enumerated(EnumType.STRING)
    private WalletTransactionType type;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "reference_id", length = 100, nullable = false,unique = true)
    private String referenceId;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PreUpdate
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PrePersist
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
