package com.shiporbit.backend.service;

import com.shiporbit.backend.dto.SignUpRequest;
import com.shiporbit.backend.dto.UserResponse;
import com.shiporbit.backend.entity.Role;
import com.shiporbit.backend.entity.Users;
import com.shiporbit.backend.exception.EmailAlreadyExistsException;
import com.shiporbit.backend.dto.JwtService;
import com.shiporbit.backend.jwt.AuthResponse;
import com.shiporbit.backend.jwt.LoginRequest;
import com.shiporbit.backend.repository.RoleRepository;
import com.shiporbit.backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RoleRepository roleRepository,
            AuthenticationManager authenticationManager,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    public UserResponse signup(SignUpRequest request) {


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
                , savedUser.getEmail()
                , savedUser.getFullName()
                , savedUser.getRole().getRole()
                ,savedUser.isEnabled());
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password())
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String accessToken = jwtService.generateToken(userDetails);

        return new AuthResponse(accessToken, "Bearer", jwtService.getExpiration() / 1000);
    }
}
