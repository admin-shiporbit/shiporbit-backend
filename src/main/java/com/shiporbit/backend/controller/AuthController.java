package com.shiporbit.backend.controller;


import com.shiporbit.backend.dto.SignUpRequest;
import com.shiporbit.backend.dto.UserResponse;
import com.shiporbit.backend.exception.UnauthorizedException;
import com.shiporbit.backend.jwt.AuthResponse;
import com.shiporbit.backend.jwt.LoginRequest;
import com.shiporbit.backend.service.AuthService;
import com.shiporbit.backend.security.ShipOrbitUserPrincipal;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/signup")
    public UserResponse signup(@Valid @RequestBody SignUpRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
    @GetMapping("/me")
    public ResponseEntity<?> me(
            Authentication authentication) {

        if(authentication == null){
            throw new UnauthorizedException("No authenticated users");
        }
        ShipOrbitUserPrincipal principal =
                (ShipOrbitUserPrincipal) authentication.getPrincipal();

        return ResponseEntity.ok(Map.of(
                "userId", principal.userId().toString(),
                "email", principal.email()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String,String>> bye(Authentication authentication){
        String userName = authentication.getName();

        SecurityContextHolder.clearContext();


        log.info("user {} logged out successfully",userName);

        return ResponseEntity.ok(Map.of("message","Logged out successfully", "user",userName));
    }
}
