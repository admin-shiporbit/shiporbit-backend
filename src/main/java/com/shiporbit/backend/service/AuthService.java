package com.shiporbit.backend.service;

import com.shiporbit.backend.dto.SignUpRequest;
import com.shiporbit.backend.dto.UserResponse;
import com.shiporbit.backend.entity.Users;

public interface AuthService {

    public UserResponse signup(SignUpRequest request) throws Exception;
}
