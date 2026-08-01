package com.shiporbit.backend.controller;


import com.shiporbit.backend.dto.SignUpRequest;
import com.shiporbit.backend.dto.UserResponse;
import com.shiporbit.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/signup")
    public UserResponse auth(@Valid @RequestBody SignUpRequest request) throws Exception {
        return authService.signup(request);
    }
}
