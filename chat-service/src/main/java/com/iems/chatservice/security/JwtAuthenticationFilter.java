package com.iems.chatservice.security;


import com.iems.chatservice.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JWT Authentication Filter
 * Handle JWT token in each request
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;
        final UUID userId;

        // Check Authorization header
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Get JWT token
            jwt = authHeader.substring(7);

            // Get username from token
            username = jwtService.extractUsername(jwt);

            userId = jwtService.extractUserId(jwt);

            // Check if user is already authenticated
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Get user information
                List<GrantedAuthority> authorities = new ArrayList<>();

// Add roles (prefix ROLE_)
                jwtService.extractRoles(jwt).forEach(role ->
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role))
                );

// Add permissions (no prefix, hoặc bạn có thể dùng PERM_)
                jwtService.extractPermissions(jwt).forEach(perm ->
                        authorities.add(new SimpleGrantedAuthority(perm))
                );

                UserDetails userDetails = new JwtUserDetails(userId,username, authorities);


                // Validate token
                if (jwtService.isTokenValid(jwt, userDetails) && jwtService.isAccessToken(jwt)) {
                    // Create authentication token
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Set authentication to SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    log.debug("User {} authenticated successfully", username);
                } else {
                    log.warn("Invalid JWT token for user: {}", username);
                }
            }
        } catch (Exception e) {
            log.warn("JWT authentication failed: {}", e.getMessage());
            // Don't throw exception to avoid breaking filter chain
        }

        filterChain.doFilter(request, response);
    }
}
