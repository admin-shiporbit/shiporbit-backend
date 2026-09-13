package com.shiporbit.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Value("${app.security.enabled:true}")
    private boolean isSecuityEnabled;

    @Value("${app.security.dev-user-email:}")
    private String devUserEmail;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint;
    private final UserDetailsService userDetailsService;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint,
            UserDetailsService userDetailsService
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jsonAuthenticationEntryPoint = jsonAuthenticationEntryPoint;
        this.userDetailsService = userDetailsService;
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
                            .requestMatchers(
                                    "/api/v1/auth/**",
                                    "/h2-console/**",
                                    "/actuator/health",
                                    "/actuator/health/**",
                                    "/actuator/info"
                            ).permitAll()

                            // Everything else requires authentication
                            .anyRequest().authenticated()
                    )

                    .csrf(csrf -> csrf.disable())

                    .sessionManagement(sm ->
                            sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    )

                    .exceptionHandling(ex ->
                            ex.authenticationEntryPoint(jsonAuthenticationEntryPoint)
                    )

                    .headers(hd ->
                            hd.frameOptions(fr -> fr.sameOrigin())
                    )

                    .addFilterBefore(
                            jwtAuthenticationFilter,
                            UsernamePasswordAuthenticationFilter.class
                    );

        } else {
            // app.security.enabled=false is a local-dev-only escape hatch. Requests still need
            // *some* ShipOrbitUserPrincipal in the SecurityContext because controllers such as
            // PickUpAddressController cast Authentication.getPrincipal() unconditionally -
            // Spring Security's default AnonymousAuthenticationFilter would otherwise leave an
            // anonymous "String" principal there, and that cast blows up with a 500 even though
            // the request itself was permitted. DevAuthenticationFilter attaches a fixed,
            // configured user instead so the rest of the app doesn't need to know security is off.
            if (devUserEmail == null || devUserEmail.isBlank()) {
                throw new IllegalStateException(
                        "app.security.enabled=false requires app.security.dev-user-email to be " +
                        "set to an existing user's email in application.yaml, so authenticated " +
                        "endpoints have a principal to act as."
                );
            }

            UserDetails devPrincipal = userDetailsService.loadUserByUsername(devUserEmail);

            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .csrf(csrf -> csrf.disable())
                    .headers(hd -> hd.frameOptions((fr -> fr.sameOrigin())))
                    .addFilterBefore(
                            new DevAuthenticationFilter(devPrincipal),
                            UsernamePasswordAuthenticationFilter.class
                    );
        }
        return http.build();
    }

    /**
     * JwtAuthenticationFilter is a @Component, so Spring Boot would otherwise auto-register it
     * as a global servlet filter on every request regardless of whether the block above wires it
     * into the Spring Security chain via addFilterBefore(). That meant it still ran (and still
     * validated any Bearer token sent) even when app.security.enabled=false. Disabling the
     * auto-registration here makes addFilterBefore() the *only* way this filter ever runs.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter
    ) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
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
