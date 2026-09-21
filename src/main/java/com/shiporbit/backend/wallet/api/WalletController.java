package com.shiporbit.backend.wallet.api;

import com.shiporbit.backend.dto.UserResponse;
import com.shiporbit.backend.security.ShipOrbitUserPrincipal;
import com.shiporbit.backend.wallet.entity.WalletEntity;
import com.shiporbit.backend.wallet.entity.WalletTransactionEntity;
import com.shiporbit.backend.wallet.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.awt.*;
import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {

    private WalletService walletService;

    @Autowired
    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/balance")
    public WalletEntity WalletBalance(Authentication authentication){
        ShipOrbitUserPrincipal principal =
                (ShipOrbitUserPrincipal) authentication.getPrincipal();

        UUID userId = principal.userId();

        return walletService.getOrCreateWallet(userId);
    }
}
