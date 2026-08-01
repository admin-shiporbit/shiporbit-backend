package com.shiporbit.backend.service;

import com.shiporbit.backend.dto.SignUpRequest;
import com.shiporbit.backend.dto.UserResponse;
import com.shiporbit.backend.entity.Role;
import com.shiporbit.backend.entity.Users;
import com.shiporbit.backend.exception.EmailAlreadyExistsException;
import com.shiporbit.backend.repository.RoleRepository;
import com.shiporbit.backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RoleRepository roleRepository;

    @Override
    @Transactional
    public UserResponse signup(SignUpRequest request) throws Exception {


        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException("Email "+email+ " is already registered with other user");
        }

//        String requestedRole = request.role()
//                .trim()
//                .toUpperCase(Locale.ROOT);

        String requestedRole = "SELLER";

        Role role = roleRepository.findByRole(requestedRole)
                .orElseThrow(
                        () -> new IllegalArgumentException("Invalid Role found" + requestedRole));

        Users user = new Users();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(role);
        user.setEnabled(true);
        user.setFullName(request.fullName().trim());

        Users savedUser = userRepository.save(user);
        return new UserResponse(
                savedUser.getId()
                , savedUser.getFullName()
                , savedUser.getEmail()
                , savedUser.getRole().getRole()
                ,savedUser.isEnabled());
    }
}
