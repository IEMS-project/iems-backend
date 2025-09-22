package com.iems.iamservice.service;

import com.iems.iamservice.dto.request.LoginRequestDto;
import com.iems.iamservice.dto.request.RefreshTokenRequestDto;
import com.iems.iamservice.dto.response.LoginResponseDto;
import com.iems.iamservice.entity.Account;

import com.iems.iamservice.entity.Permission;
import com.iems.iamservice.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service handling authentication and authorization
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AccountService accountService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    private UserRolePermissionService userRolePermissionService;

    /**
     * User login
     */
    @Transactional
    public LoginResponseDto login(LoginRequestDto loginRequest) {
        log.info("Attempting login for user: {}", loginRequest.getUsernameOrEmail());

        try {
            // Authenticate user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsernameOrEmail(),
                            loginRequest.getPassword()
                    )
            );

            // Get user information
            Account user = accountService.findByUsernameOrEmail(loginRequest.getUsernameOrEmail())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Check if account is locked
            if (!user.getEnabled()) {
                log.warn("Login attempt for locked account: {}", loginRequest.getUsernameOrEmail());
                throw new RuntimeException("Account is locked");
            }

            // Update last login time
            user.setLastLoginAt(Instant.now());
            accountService.save(user);

            // Get roles and permissions information
            Set<String> roles = userRolePermissionService.getUserRoles(user.getUserId()).stream()
                    .map(Role::getCode)
                    .collect(Collectors.toSet());

            Set<String> permissions = userRolePermissionService.getAllUserPermissions(user.getUserId()).stream()
                    .map(Permission::getCode)
                    .collect(Collectors.toSet());

            // Create tokens with roles and permissions
            String accessToken = jwtService.generateTokenWithUserInfo(
                    user.getUserId(), user.getUsername(), user.getEmail(), roles, permissions);
            String refreshToken = jwtService.generateRefreshTokenWithUserId(
                    user.getUserId(), user.getUsername());


            log.info("Successful login for user: {} with userId: {}", user.getUsername(), user.getUserId());

            return LoginResponseDto.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(jwtService.getAccessTokenExpiration())
                    .expiresAt(Instant.now().plusSeconds(jwtService.getAccessTokenExpiration()))
                    .userInfo(LoginResponseDto.UserInfoDto.builder()
                            .userId(user.getUserId())
                            .username(user.getUsername())
                            .email(user.getEmail())
                            .roles(roles)
                            .permissions(permissions)
                            .enabled(user.getEnabled())
                            .lastLoginAt(user.getLastLoginAt())
                            .build())
                    .build();

        } catch (Exception e) {
            log.error("Authentication failed for user: {} - Error: {}", loginRequest.getUsernameOrEmail(), e.getMessage(), e);
            throw new RuntimeException("Invalid username or password");
        }
    }

    /**
     * Refresh access token
     */
    @Transactional
    public LoginResponseDto refreshToken(RefreshTokenRequestDto refreshRequest) {
        log.info("Attempting token refresh");

        try {
            // Validate refresh token
            if (!jwtService.isRefreshToken(refreshRequest.getRefreshToken())) {
                throw new RuntimeException("Invalid refresh token");
            }

            if (jwtService.isTokenExpired(refreshRequest.getRefreshToken())) {
                throw new RuntimeException("Refresh token has expired");
            }

            // Get information from refresh token
            String username = jwtService.extractUsername(refreshRequest.getRefreshToken());
            UUID userId = jwtService.extractUserId(refreshRequest.getRefreshToken());

            // Check if user still exists and is active
            Account user = accountService.findByUsernameOrEmail(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (!user.getEnabled()) {
                throw new RuntimeException("Account is locked");
            }

            // Get roles and permissions information
            Set<String> roles = userRolePermissionService.getUserRoles(user.getUserId()).stream()
                    .map(Role::getCode)
                    .collect(Collectors.toSet());

            Set<String> permissions = userRolePermissionService.getAllUserPermissions(user.getUserId()).stream()
                    .map(Permission::getCode)
                    .collect(Collectors.toSet());

            // Create new access token with roles and permissions
            String newAccessToken = jwtService.generateTokenWithUserInfo(
                    user.getUserId(), user.getUsername(), user.getEmail(), roles, permissions);

            // Create new refresh token
            String newRefreshToken = jwtService.generateRefreshTokenWithUserId(
                    user.getUserId(), user.getUsername());

            log.info("Successful token refresh for user: {}", username);

            return LoginResponseDto.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .tokenType("Bearer")
                    .expiresIn(jwtService.getAccessTokenExpiration())
                    .expiresAt(Instant.now().plusSeconds(jwtService.getAccessTokenExpiration()))
                    .userInfo(LoginResponseDto.UserInfoDto.builder()
                            .userId(user.getUserId())
                            .username(user.getUsername())
                            .email(user.getEmail())
                            .roles(roles)
                            .permissions(permissions)
                            .enabled(user.getEnabled())
                            .lastLoginAt(user.getLastLoginAt())
                            .build())
                    .build();

        } catch (Exception e) {
            log.warn("Token refresh failed: {}", e.getMessage());
            throw new RuntimeException("Cannot refresh token: " + e.getMessage());
        }
    }

    /**
     * Logout (can implement token blacklist if needed)
     */
    public void logout(String token) {
        log.info("User logout");
        // TODO: Implement token blacklist if needed
        // For now, just log the logout action
    }

    /**
     * Validate token
     */
    public boolean validateToken(String token) {
        try {
            return !jwtService.isTokenExpired(token) && jwtService.isAccessToken(token);
        } catch (Exception e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get user information from token
     */
    public Account getUserFromToken(String token) {
        try {
            String username = jwtService.extractUsername(token);
            return accountService.findByUsernameOrEmail(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));
        } catch (Exception e) {
            log.warn("Failed to get user from token: {}", e.getMessage());
            throw new RuntimeException("Invalid token");
        }
    }

    /**
     * Get user information with roles and permissions from token
     */
    public LoginResponseDto.UserInfoDto getUserInfoFromToken(String token) {
        try {
            String username = jwtService.extractUsername(token);
            UUID userId = jwtService.extractUserId(token);
            String email = jwtService.extractEmail(token);
            Set<String> roles = jwtService.extractRoles(token);
            Set<String> permissions = jwtService.extractPermissions(token);

            Account user = accountService.findByUsernameOrEmail(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            return LoginResponseDto.UserInfoDto.builder()
                    .userId(userId)
                    .username(username)
                    .email(email)
                    .roles(roles)
                    .permissions(permissions)
                    .enabled(user.getEnabled())
                    .lastLoginAt(user.getLastLoginAt())
                    .build();
        } catch (Exception e) {
            log.warn("Failed to get user info from token: {}", e.getMessage());
            throw new RuntimeException("Invalid token");
        }
    }
}
