package com.shiporbit.backend.service;

import com.shiporbit.backend.dto.SignUpRequest;
import com.shiporbit.backend.dto.UserResponse;
import com.shiporbit.backend.jwt.AuthResponse;
import com.shiporbit.backend.jwt.LoginRequest;

public interface AuthService {

    UserResponse signup(SignUpRequest request);

    AuthResponse login(LoginRequest request);
}
