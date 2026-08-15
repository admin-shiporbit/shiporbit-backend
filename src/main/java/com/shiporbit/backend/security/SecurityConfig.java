package com.shiporbit.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Value("app.security.enabled:true")
    private boolean isSecuityEnabled;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        if (isSecuityEnabled) {
            http.authorizeHttpRequests(auth -> auth

                            // This specific endpoint requires JWT
                            .requestMatchers(
                                    "/api/v1/auth/me",
                                    "/api/v1/auth/logout"
                            ).authenticated()

                            // Keep all your existing auth APIs public
                            .requestMatchers("/api/v1/auth/**", "/h2-console/**").permitAll()

                            // Everything else requires authentication
                            .anyRequest().authenticated()
                    )

                    .csrf(csrf -> csrf.disable())

                    .sessionManagement(sm ->
                            sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    )

                    .exceptionHandling(ex ->
                            ex.authenticationEntryPoint(
                                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
                            )
                    )

                    .headers(hd ->
                            hd.frameOptions(fr -> fr.sameOrigin())
                    )

                    .addFilterBefore(
                            jwtAuthenticationFilter,
                            UsernamePasswordAuthenticationFilter.class
                    );

        } else {
            http.authorizeHttpRequests(auth->auth.anyRequest().permitAll());
        }
        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
