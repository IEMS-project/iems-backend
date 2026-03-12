package com.iems.iamservice.service;

import com.iems.iamservice.dto.request.LoginRequestDto;
import com.iems.iamservice.dto.request.RefreshTokenRequestDto;
import com.iems.iamservice.dto.request.RegisterRequestDto;
import com.iems.iamservice.dto.response.LoginResponseDto;
import com.iems.iamservice.dto.response.RegisterResponseDto;
import com.iems.iamservice.entity.Account;
import com.iems.iamservice.entity.User;
import com.iems.iamservice.exception.AppException;
import com.iems.iamservice.exception.ErrorCode;

import com.iems.iamservice.entity.Role;
import com.iems.iamservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
    private final UserRepository userRepository;
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
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsernameOrEmail(),
                            loginRequest.getPassword()
                    )
            );

            // Get user information
            Account user = accountService.findByUsernameOrEmail(loginRequest.getUsernameOrEmail())
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND_BY_EMAIL));

            // Check if account is locked
            if (!user.getEnabled()) {
                log.warn("Login attempt for locked account: {}", loginRequest.getUsernameOrEmail());
                throw new AppException(ErrorCode.ACCOUNT_LOCKED);
            }

            // Update last login time
            user.setLastLoginAt(Instant.now());
            accountService.save(user);

            // Get roles information
            Set<String> roles = userRolePermissionService.getUserRoles(user.getId()).stream()
                    .map(Role::getCode)
                    .collect(Collectors.toSet());

            // Create tokens with roles
            String accessToken = jwtService.generateTokenWithUserInfo(
                    user.getId(), user.getUsername(), user.getEmail(), roles);
            String refreshToken = jwtService.generateRefreshTokenWithUserId(
                    user.getId(), user.getUsername());


            log.info("Successful login for user: {} with accountId: {}", user.getUsername(), user.getId());

            return LoginResponseDto.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(jwtService.getAccessTokenExpiration())
                    .expiresAt(Instant.now().plusSeconds(jwtService.getAccessTokenExpiration()))
                    .userInfo(LoginResponseDto.UserInfoDto.builder()
                            .userId(user.getId())
                            .username(user.getUsername())
                            .email(user.getEmail())
                            .roles(roles)
                            .enabled(user.getEnabled())
                            .lastLoginAt(user.getLastLoginAt())
                            .build())
                    .build();

        } catch (Exception e) {
            log.warn("Authentication failed for user: {}", loginRequest.getUsernameOrEmail());
            throw new AppException(ErrorCode.LOGIN_FAILED);
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
                throw new AppException(ErrorCode.INVALID_TOKEN);
            }

            if (jwtService.isTokenExpired(refreshRequest.getRefreshToken())) {
                throw new AppException(ErrorCode.REFRESH_TOKEN_EXPIRED);
            }

            // Get information from refresh token
            String username = jwtService.extractUsername(refreshRequest.getRefreshToken());

            // Check if user still exists and is active
            Account user = accountService.findByUsernameOrEmail(username)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND_BY_EMAIL));

            if (!user.getEnabled()) {
                throw new AppException(ErrorCode.ACCOUNT_LOCKED);
            }

            // Get roles information
            Set<String> roles = userRolePermissionService.getUserRoles(user.getId()).stream()
                    .map(Role::getCode)
                    .collect(Collectors.toSet());

            // Create new access token with roles
            String newAccessToken = jwtService.generateTokenWithUserInfo(
                    user.getId(), user.getUsername(), user.getEmail(), roles);

            // Create new refresh token
            String newRefreshToken = jwtService.generateRefreshTokenWithUserId(
                    user.getId(), user.getUsername());

            log.info("Successful token refresh for user: {}", username);

            return LoginResponseDto.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .tokenType("Bearer")
                    .expiresIn(jwtService.getAccessTokenExpiration())
                    .expiresAt(Instant.now().plusSeconds(jwtService.getAccessTokenExpiration()))
                    .userInfo(LoginResponseDto.UserInfoDto.builder()
                            .userId(user.getId())
                            .username(user.getUsername())
                            .email(user.getEmail())
                            .roles(roles)
                            .enabled(user.getEnabled())
                            .lastLoginAt(user.getLastLoginAt())
                            .build())
                    .build();

        } catch (AppException e) {
            log.warn("Token refresh failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("Token refresh failed: {}", e.getMessage());
            throw new AppException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
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
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND_IN_TOKEN));
        } catch (AppException e) {
            log.warn("Failed to get user from token: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("Failed to get user from token: {}", e.getMessage());
            throw new AppException(ErrorCode.TOKEN_VALIDATION_FAILED);
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
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND_IN_TOKEN));

            return LoginResponseDto.UserInfoDto.builder()
                    .userId(userId)
                    .username(username)
                    .email(email)
                    .roles(roles)
                    .permissions(permissions)
                    .enabled(user.getEnabled())
                    .lastLoginAt(user.getLastLoginAt())
                    .build();
        } catch (AppException e) {
            log.warn("Failed to get user info from token: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("Failed to get user info from token: {}", e.getMessage());
            throw new AppException(ErrorCode.TOKEN_VALIDATION_FAILED);
        }
    }

    /**
     * User registration
     */
    @Transactional
    public RegisterResponseDto register(RegisterRequestDto registerRequest) {
        log.info("Attempting registration for user: {}", registerRequest.getUsername());

        try {
            // Check if username already exists
            if (accountService.existsByUsername(registerRequest.getUsername())) {
                throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
            }

            // Check if email already exists
            if (accountService.existsByEmail(registerRequest.getEmail())) {
                throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }

            // Create Account
            Account account = Account.builder()
                    .username(registerRequest.getUsername())
                    .email(registerRequest.getEmail())
                    .passwordHash(passwordEncoder.encode(registerRequest.getPassword()))
                    .enabled(true)
                    .createdAt(Instant.now())
                    .build();

            Account savedAccount = accountService.save(account);

            // Create User Profile
            User user = User.builder()
                    .accountId(savedAccount.getId())
                    .firstName(registerRequest.getFirstName())
                    .lastName(registerRequest.getLastName())
                    .email(registerRequest.getEmail())
                    .address(registerRequest.getAddress())
                    .phone(registerRequest.getPhone())
                    .dob(registerRequest.getDob())
                    .gender(registerRequest.getGender())
                    .image(registerRequest.getImage())
                    .createdAt(Instant.now())
                    .build();

            User savedUser = userRepository.save(user);

            log.info("Successfully registered user: {} with accountId: {}", savedAccount.getUsername(), savedAccount.getId());

            return RegisterResponseDto.builder()
                    .accountId(savedAccount.getId())
                    .userId(savedUser.getId())
                    .username(savedAccount.getUsername())
                    .email(savedAccount.getEmail())
                    .firstName(savedUser.getFirstName())
                    .lastName(savedUser.getLastName())
                    .message("Registration successful. You can now login.")
                    .build();

        } catch (AppException e) {
            log.warn("Registration failed for user {}: {}", registerRequest.getUsername(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during registration for user {}: {}", registerRequest.getUsername(), e.getMessage(), e);
            throw new AppException(ErrorCode.REGISTRATION_FAILED);
        }
    }
}
