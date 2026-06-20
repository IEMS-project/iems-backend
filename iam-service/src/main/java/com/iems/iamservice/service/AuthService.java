package com.iems.iamservice.service;

import com.iems.iamservice.dto.request.LoginRequestDto;
import com.iems.iamservice.dto.request.OAuthCodeRequestDto;
import com.iems.iamservice.dto.request.OAuthIdTokenRequestDto;
import com.iems.iamservice.dto.request.RefreshTokenRequestDto;
import com.iems.iamservice.dto.request.RegisterRequestDto;
import com.iems.iamservice.dto.response.LoginResponseDto;
import com.iems.iamservice.dto.response.RegisterResponseDto;
import com.iems.iamservice.entity.Account;
import com.iems.iamservice.entity.User;
import com.iems.iamservice.exception.AppException;
import com.iems.iamservice.exception.ErrorCode;
import com.iems.iamservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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
    private final WebClient.Builder webClientBuilder;

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${google.client-secret}")
    private String googleClientSecret;

    @Value("${google.redirect-uri}")
    private String googleRedirectUri;

    @Value("${github.client-id}")
    private String githubClientId;

    @Value("${github.client-secret}")
    private String githubClientSecret;

    @Value("${github.redirect-uri}")
    private String githubRedirectUri;

    @Autowired
    private UserRolePermissionService userRolePermissionService;

    private static final String DEFAULT_ROLE = "USER";

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
            user = accountService.normalizeExpiredSubscription(user);

            // Get roles information
            Set<String> roles = userRolePermissionService.getUserRoles(user.getId());

            // Create tokens with roles and subscription info
            String accessToken = jwtService.generateTokenWithUserInfo(
                    user.getId(), user.getUsername(), user.getEmail(), roles,
                    user.getSubscriptionType(), user.getPremiumUntil());
            String refreshToken = jwtService.generateRefreshTokenWithUserId(
                    user.getId(), user.getUsername());


            log.info("Successful login for user: {} with accountId: {}", user.getUsername(), user.getId());

                return buildLoginResponse(user, roles, accessToken, refreshToken);

        } catch (Exception e) {
            log.warn("Authentication failed for user: {}", loginRequest.getUsernameOrEmail());
            throw new AppException(ErrorCode.LOGIN_FAILED);
        }
    }

    /**
     * Login or register with Google by Authorization Code.
     */
    @Transactional
    public LoginResponseDto loginWithGoogleCode(OAuthCodeRequestDto request) {
        String googleAccessToken = exchangeGoogleCodeForAccessToken(request.getCode());
        OAuthProfile profile = fetchGoogleProfile(googleAccessToken);
        return loginWithOAuthProfile("google", profile);
    }

    /**
     * Login or register with Google by ID Token.
     */
    @Transactional
    public LoginResponseDto loginWithGoogleIdToken(OAuthIdTokenRequestDto request) {
        OAuthProfile profile = fetchGoogleProfileFromIdToken(request.getIdToken());
        return loginWithOAuthProfile("google", profile);
    }

    /**
     * Login or register with GitHub by Authorization Code.
     */
    @Transactional
    public LoginResponseDto loginWithGithubCode(OAuthCodeRequestDto request) {
        String githubAccessToken = exchangeGithubCodeForAccessToken(request.getCode());
        OAuthProfile profile = fetchGithubProfile(githubAccessToken);
        return loginWithOAuthProfile("github", profile);
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
            user = accountService.normalizeExpiredSubscription(user);

            if (!user.getEnabled()) {
                throw new AppException(ErrorCode.ACCOUNT_LOCKED);
            }

            // Get roles information
            Set<String> roles = userRolePermissionService.getUserRoles(user.getId());

            // Create new access token with roles and subscription info
            String newAccessToken = jwtService.generateTokenWithUserInfo(
                    user.getId(), user.getUsername(), user.getEmail(), roles,
                    user.getSubscriptionType(), user.getPremiumUntil());

            // Create new refresh token
            String newRefreshToken = jwtService.generateRefreshTokenWithUserId(
                    user.getId(), user.getUsername());

            log.info("Successful token refresh for user: {}", username);

                return buildLoginResponse(user, roles, newAccessToken, newRefreshToken);

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
            // Set<String> permissions = jwtService.extractPermissions(token);

            Account user = accountService.findByUsernameOrEmail(username)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND_IN_TOKEN));

            return LoginResponseDto.UserInfoDto.builder()
                    .userId(userId)
                    .username(username)
                    .email(email)
                    .roles(roles)
                    // .permissions(permissions)
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

    private LoginResponseDto loginWithOAuthProfile(String provider, OAuthProfile profile) {
        if (profile.email() == null || profile.email().isBlank()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        String normalizedEmail = profile.email().trim().toLowerCase();

        Account account = accountService.findByEmail(normalizedEmail)
                .orElseGet(() -> createOAuthAccount(provider, normalizedEmail, profile.name(), profile.avatarUrl()));

        if (!Boolean.TRUE.equals(account.getEnabled())) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }

        account.setLastLoginAt(Instant.now());
        accountService.save(account);

        Set<String> roles = userRolePermissionService.getUserRoles(account.getId());
        if (roles == null || roles.isEmpty()) {
            userRolePermissionService.assignRolesToUser(account.getId(), Set.of(DEFAULT_ROLE));
            roles = userRolePermissionService.getUserRoles(account.getId());
        }
        account = accountService.normalizeExpiredSubscription(account);

        String accessToken = jwtService.generateTokenWithUserInfo(
                account.getId(), account.getUsername(), account.getEmail(), roles,
                account.getSubscriptionType(), account.getPremiumUntil());
        String refreshToken = jwtService.generateRefreshTokenWithUserId(
                account.getId(), account.getUsername());

        return buildLoginResponse(account, roles, accessToken, refreshToken);
    }

    private Account createOAuthAccount(String provider, String email, String displayName, String avatarUrl) {
        String username = buildUniqueUsername(email);

        Account account = Account.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(provider + "_" + UUID.randomUUID()))
                .enabled(true)
                .createdAt(Instant.now())
                .build();

        Account savedAccount = accountService.save(account);
        userRolePermissionService.assignRolesToUser(savedAccount.getId(), Set.of(DEFAULT_ROLE));

        if (!userRepository.existsByAccountId(savedAccount.getId())) {
            NameParts nameParts = splitName(displayName, username);
            User profile = User.builder()
                    .accountId(savedAccount.getId())
                    .firstName(nameParts.firstName())
                    .lastName(nameParts.lastName())
                    .email(email)
                    .image(avatarUrl)
                    .createdAt(Instant.now())
                    .build();
            userRepository.save(profile);
        }

        log.info("Created new {} OAuth account with id {} and email {}", provider, savedAccount.getId(), email);
        return savedAccount;
    }

    private String buildUniqueUsername(String email) {
        String localPart = email.split("@")[0];
        String base = localPart.replaceAll("[^a-zA-Z0-9._-]", "").trim();
        if (base.isBlank()) {
            base = "user";
        }

        String candidate = base;
        int suffix = 1;
        while (accountService.existsByUsername(candidate)) {
            candidate = base + suffix;
            suffix++;
        }
        return candidate;
    }

    private NameParts splitName(String rawName, String fallbackFirstName) {
        if (rawName == null || rawName.isBlank()) {
            return new NameParts(fallbackFirstName, "OAuth");
        }
        String[] parts = rawName.trim().split("\\s+");
        if (parts.length == 1) {
            return new NameParts(parts[0], "OAuth");
        }

        String firstName = parts[0];
        StringBuilder lastNameBuilder = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            if (!lastNameBuilder.isEmpty()) {
                lastNameBuilder.append(' ');
            }
            lastNameBuilder.append(parts[i]);
        }
        return new NameParts(firstName, lastNameBuilder.toString());
    }

    private String exchangeGoogleCodeForAccessToken(String code) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("code", code);
        formData.add("client_id", googleClientId);
        formData.add("client_secret", googleClientSecret);
        formData.add("redirect_uri", googleRedirectUri);
        formData.add("grant_type", "authorization_code");

        Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        String accessToken = response == null ? null : (String) response.get("access_token");
        if (accessToken == null || accessToken.isBlank()) {
            log.warn("Google token exchange failed: {}", response);
            throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
        return accessToken;
    }

    private OAuthProfile fetchGoogleProfile(String accessToken) {
        Map<String, Object> response = webClientBuilder.build()
                .get()
                .uri("https://www.googleapis.com/oauth2/v3/userinfo")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) {
            throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }

        return new OAuthProfile(
                asString(response.get("email")),
                asString(response.get("name")),
                asString(response.get("picture"))
        );
    }

    private OAuthProfile fetchGoogleProfileFromIdToken(String idToken) {
        Map<String, Object> response = webClientBuilder.build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("oauth2.googleapis.com")
                        .path("/tokeninfo")
                        .queryParam("id_token", idToken)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) {
            throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }

        return new OAuthProfile(
                asString(response.get("email")),
                asString(response.get("name")),
                asString(response.get("picture"))
        );
    }

    private String exchangeGithubCodeForAccessToken(String code) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", githubClientId);
        formData.add("client_secret", githubClientSecret);
        formData.add("code", code);
        formData.add("redirect_uri", githubRedirectUri);

        Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri("https://github.com/login/oauth/access_token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        String accessToken = response == null ? null : (String) response.get("access_token");
        if (accessToken == null || accessToken.isBlank()) {
            log.warn("GitHub token exchange failed: {}", response);
            throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
        return accessToken;
    }

    private OAuthProfile fetchGithubProfile(String accessToken) {
        Map<String, Object> userResponse = webClientBuilder.build()
                .get()
                .uri("https://api.github.com/user")
                .headers(headers -> {
                    headers.setBearerAuth(accessToken);
                    headers.set("Accept", "application/vnd.github+json");
                    headers.set("User-Agent", "iems-iam-service");
                })
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (userResponse == null) {
            throw new AppException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }

        String email = asString(userResponse.get("email"));
        if (email == null || email.isBlank()) {
            email = fetchGithubPrimaryEmail(accessToken);
        }

        String name = asString(userResponse.get("name"));
        if (name == null || name.isBlank()) {
            name = asString(userResponse.get("login"));
        }

        return new OAuthProfile(email, name, asString(userResponse.get("avatar_url")));
    }

    private String fetchGithubPrimaryEmail(String accessToken) {
        List<Map<String, Object>> emails = webClientBuilder.build()
                .get()
                .uri("https://api.github.com/user/emails")
                .headers(headers -> {
                    headers.setBearerAuth(accessToken);
                    headers.set("Accept", "application/vnd.github+json");
                    headers.set("User-Agent", "iems-iam-service");
                })
                .retrieve()
                .bodyToMono(List.class)
                .block();

        if (emails == null || emails.isEmpty()) {
            return null;
        }

        for (Map<String, Object> emailEntry : emails) {
            boolean primary = Boolean.TRUE.equals(emailEntry.get("primary"));
            boolean verified = Boolean.TRUE.equals(emailEntry.get("verified"));
            if (primary && verified) {
                return asString(emailEntry.get("email"));
            }
        }

        for (Map<String, Object> emailEntry : emails) {
            if (Boolean.TRUE.equals(emailEntry.get("verified"))) {
                return asString(emailEntry.get("email"));
            }
        }

        return asString(emails.getFirst().get("email"));
    }

    private String asString(Object value) {
        return value == null ? null : Objects.toString(value, null);
    }

    private LoginResponseDto buildLoginResponse(Account user, Set<String> roles, String accessToken, String refreshToken) {
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
    }

    private record OAuthProfile(String email, String name, String avatarUrl) {
    }

    private record NameParts(String firstName, String lastName) {
    }
}
