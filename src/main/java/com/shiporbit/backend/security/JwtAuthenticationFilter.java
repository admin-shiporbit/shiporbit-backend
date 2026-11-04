package com.shiporbit.backend.security;

import com.shiporbit.backend.dto.JwtService;
import com.shiporbit.backend.logging.Flow;
import com.shiporbit.backend.logging.Mask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                Flow.step(LOGGER, "Auth: no Bearer token (public endpoint, dev user or 401 follows)");
            }
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(7);
        try {
            String email = jwtService.extractEmail(token);
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                ShipOrbitUserPrincipal userDetails =
                        (ShipOrbitUserPrincipal) userDetailsService.loadUserByUsername(email);
                if (!jwtService.isTokenValid(token, userDetails)) {
                    Flow.stepWarn(LOGGER, "Auth: JWT rejected for {} (expired or not matching user)", Mask.email(email));
                }
                if (jwtService.isTokenValid(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    Flow.step(LOGGER, "Auth: JWT valid - user {} ({}), roles {}", Mask.email(email),
                            userDetails.userId(), userDetails.getAuthorities());
                }
            }
        } catch (JwtException | IllegalArgumentException e) {
            Flow.stepWarn(LOGGER, "Auth: invalid JWT ({})", e.getClass().getSimpleName());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
