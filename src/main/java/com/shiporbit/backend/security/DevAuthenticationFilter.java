package com.shiporbit.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Only wired into the filter chain by SecurityConfig when app.security.enabled=false.
 *
 * Spring Security still runs its default AnonymousAuthenticationFilter even when every
 * request is permitAll()'d, so without this filter Authentication.getPrincipal() would be
 * the String "anonymousUser" instead of a ShipOrbitUserPrincipal. Controllers such as
 * PickUpAddressController unconditionally cast the principal to ShipOrbitUserPrincipal,
 * so that mismatch surfaced as a ClassCastException (500) on every call made without a
 * JWT - effectively still requiring authentication even with the flag turned off.
 *
 * This filter stands in for the JWT filter in that scenario: it attaches a single,
 * configured "dev user" (app.security.dev-user-email) as the authenticated principal for
 * every request, so existing controller code keeps working unmodified.
 */
public class DevAuthenticationFilter extends OncePerRequestFilter {

    private final UserDetails devPrincipal;

    public DevAuthenticationFilter(UserDetails devPrincipal) {
        this.devPrincipal = devPrincipal;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            devPrincipal,
                            null,
                            devPrincipal.getAuthorities()
                    );
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
