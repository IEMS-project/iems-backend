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
                            loginRequest.getPassword()));

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
            log.info("Successful login for user: {} with accountId: {}", user.getUsername(), user.getId());
            return buildLoginResponse(user, roles);

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Authentication failed for user: {}", loginRequest.getUsernameOrEmail());
            throw new AppException(ErrorCode.LOGIN_FAILED);
        }
    }

    /**
     * Login/Register with Google (auto-provision account if not exists)
     */
    @Transactional
    public LoginResponseDto authenticateWithGoogle(String idToken) {
        log.info("Attempting Google authentication");

        GoogleTokenInfo tokenInfo = verifyGoogleIdToken(idToken);
        validateGoogleTokenInfo(tokenInfo);

        String email = tokenInfo.getEmail().trim().toLowerCase(Locale.ROOT);

        Account account = accountService.findByUsernameOrEmail(email)
                .orElseGet(() -> createGoogleAccount(tokenInfo, email));

        if (!account.getEnabled()) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }

        ensureGoogleUserProfile(account, tokenInfo, email);

        account.setLastLoginAt(Instant.now());
        accountService.save(account);

        Set<String> roles = userRolePermissionService.getUserRoles(account.getId());
        log.info("Google auth successful for email: {} accountId: {}", email, account.getId());
        return buildLoginResponse(account, roles);
    }

    @Transactional
    public LoginResponseDto authenticateWithGoogleCode(String code) {
        log.info("Attempting Google authentication with authorization code");

        if (!StringUtils.hasText(googleClientId)
                || !StringUtils.hasText(googleClientSecret)
                || !StringUtils.hasText(googleRedirectUri)) {
            log.error("Google OAuth code flow is not configured. clientId={}, redirectUri={}",
                    StringUtils.hasText(googleClientId), StringUtils.hasText(googleRedirectUri));
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        String idToken = exchangeGoogleCodeForIdToken(code);
        return authenticateWithGoogle(idToken);
    }

    private String exchangeGoogleCodeForIdToken(String code) {
        if (!StringUtils.hasText(code)) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        try {
            GoogleTokenExchangeResponse response = WebClient.create("https://oauth2.googleapis.com")
                    .post()
                    .uri("/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromFormData("client_id", googleClientId)
                            .with("client_secret", googleClientSecret)
                            .with("code", code)
                            .with("grant_type", "authorization_code")
                            .with("redirect_uri", googleRedirectUri))
                    .retrieve()
                    .bodyToMono(GoogleTokenExchangeResponse.class)
                    .block();

            if (response == null || !StringUtils.hasText(response.getIdToken())) {
                throw new AppException(ErrorCode.INVALID_TOKEN);
            }

            return response.getIdToken();
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Google code exchange failed: {}", ex.getMessage());
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }

    /**
     * Login/Register with GitHub (auto-provision account if not exists)
     */
    @Transactional
    public LoginResponseDto authenticateWithGithub(String code) {
        log.info("Attempting GitHub authentication");

        validateGithubConfig();

        GithubAccessTokenResponse tokenResponse = exchangeGithubCodeForAccessToken(code);
        if (tokenResponse == null || !StringUtils.hasText(tokenResponse.getAccessToken())) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        GithubUserInfo githubUserInfo = fetchGithubUserInfo(tokenResponse.getAccessToken());
        String email = resolveGithubEmail(tokenResponse.getAccessToken(), githubUserInfo);

        Account account = accountService.findByUsernameOrEmail(email)
                .orElseGet(() -> createGithubAccount(githubUserInfo, email));

        if (!account.getEnabled()) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }

        ensureGithubUserProfile(account, githubUserInfo, email);

        account.setLastLoginAt(Instant.now());
        accountService.save(account);

        Set<String> roles = userRolePermissionService.getUserRoles(account.getId());
        log.info("GitHub auth successful for email: {} accountId: {}", email, account.getId());
        return buildLoginResponse(account, roles);
    }

    private void validateGithubConfig() {
        if (!StringUtils.hasText(githubClientId)
                || !StringUtils.hasText(githubClientSecret)
                || !StringUtils.hasText(githubRedirectUri)) {
            log.error("GitHub OAuth is not configured. clientId={}, redirectUri={}",
                    StringUtils.hasText(githubClientId), StringUtils.hasText(githubRedirectUri));
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private GithubAccessTokenResponse exchangeGithubCodeForAccessToken(String code) {
        if (!StringUtils.hasText(code)) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        try {
            return WebClient.create("https://github.com")
                    .post()
                    .uri("/login/oauth/access_token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromFormData("client_id", githubClientId)
                            .with("client_secret", githubClientSecret)
                            .with("code", code)
                            .with("redirect_uri", githubRedirectUri))
                    .retrieve()
                    .bodyToMono(GithubAccessTokenResponse.class)
                    .block();
        } catch (Exception ex) {
            log.warn("GitHub token exchange failed: {}", ex.getMessage());
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }

    private GithubUserInfo fetchGithubUserInfo(String accessToken) {
        try {
            GithubUserInfo githubUserInfo = WebClient.create("https://api.github.com")
                    .get()
                    .uri("/user")
                    .headers(headers -> {
                        headers.setBearerAuth(accessToken);
                        headers.set("Accept", "application/vnd.github+json");
                    })
                    .retrieve()
                    .bodyToMono(GithubUserInfo.class)
                    .block();

            if (githubUserInfo == null) {
                throw new AppException(ErrorCode.INVALID_TOKEN);
            }

            return githubUserInfo;
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Failed to fetch GitHub profile: {}", ex.getMessage());
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }

    private String resolveGithubEmail(String accessToken, GithubUserInfo githubUserInfo) {
        if (StringUtils.hasText(githubUserInfo.getEmail())) {
            return githubUserInfo.getEmail().trim().toLowerCase(Locale.ROOT);
        }

        try {
            List<GithubEmailInfo> emails = WebClient.create("https://api.github.com")
                    .get()
                    .uri("/user/emails")
                    .headers(headers -> {
                        headers.setBearerAuth(accessToken);
                        headers.set("Accept", "application/vnd.github+json");
                    })
                    .retrieve()
                    .bodyToFlux(GithubEmailInfo.class)
                    .collectList()
                    .block();

            if (emails == null || emails.isEmpty()) {
                throw new AppException(ErrorCode.INVALID_TOKEN);
            }

            for (GithubEmailInfo emailInfo : emails) {
                if (emailInfo.isPrimary() && emailInfo.isVerified() && StringUtils.hasText(emailInfo.getEmail())) {
                    return emailInfo.getEmail().trim().toLowerCase(Locale.ROOT);
                }
            }

            for (GithubEmailInfo emailInfo : emails) {
                if (emailInfo.isVerified() && StringUtils.hasText(emailInfo.getEmail())) {
                    return emailInfo.getEmail().trim().toLowerCase(Locale.ROOT);
                }
            }

            throw new AppException(ErrorCode.INVALID_TOKEN);
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Failed to resolve GitHub email: {}", ex.getMessage());
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }

    private GoogleTokenInfo verifyGoogleIdToken(String idToken) {
        if (!StringUtils.hasText(idToken)) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        try {
            return WebClient.create("https://oauth2.googleapis.com")
                    .get()
                    .uri(uriBuilder -> uriBuilder.path("/tokeninfo").queryParam("id_token", idToken).build())
                    .retrieve()
                    .bodyToMono(GoogleTokenInfo.class)
                    .block();
        } catch (Exception ex) {
            log.warn("Google token verification failed: {}", ex.getMessage());
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }

    private void validateGoogleTokenInfo(GoogleTokenInfo tokenInfo) {
        if (tokenInfo == null || !StringUtils.hasText(tokenInfo.getEmail())) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        if (StringUtils.hasText(googleClientId) && !googleClientId.equals(tokenInfo.getAud())) {
            log.warn("Google token aud mismatch. Expected {}, got {}", googleClientId, tokenInfo.getAud());
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        if (!tokenInfo.isEmailVerified()) {
            log.warn("Google email is not verified for {}", tokenInfo.getEmail());
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }

    private Account createGoogleAccount(GoogleTokenInfo tokenInfo, String email) {
        String username = generateUniqueUsername(email, "googleuser");

        Account account = Account.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .enabled(true)
                .createdAt(Instant.now())
                .build();

        Account savedAccount = accountService.save(account);

        try {
            userRolePermissionService.assignRolesToUser(savedAccount.getId(), Set.of("USER"));
        } catch (Exception ex) {
            log.warn("Failed to assign default USER role to Google account {}: {}", savedAccount.getId(),
                    ex.getMessage());
        }

        return savedAccount;
    }

    private Account createGithubAccount(GithubUserInfo githubUserInfo, String email) {
        String username = StringUtils.hasText(githubUserInfo.getLogin())
                ? sanitizeUsername(githubUserInfo.getLogin())
                : generateUniqueUsername(email, "githubuser");

        if (!StringUtils.hasText(username) || accountService.existsByUsername(username)) {
            username = generateUniqueUsername(email, "githubuser");
        }

        Account account = Account.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .enabled(true)
                .createdAt(Instant.now())
                .build();

        Account savedAccount = accountService.save(account);

        try {
            userRolePermissionService.assignRolesToUser(savedAccount.getId(), Set.of("USER"));
        } catch (Exception ex) {
            log.warn("Failed to assign default USER role to GitHub account {}: {}", savedAccount.getId(),
                    ex.getMessage());
        }

        return savedAccount;
    }

    private void ensureGoogleUserProfile(Account account, GoogleTokenInfo tokenInfo, String email) {
        if (userRepository.existsByAccountId(account.getId())) {
            return;
        }

        String firstName = StringUtils.hasText(tokenInfo.getGivenName()) ? tokenInfo.getGivenName() : "Google";
        String lastName = StringUtils.hasText(tokenInfo.getFamilyName()) ? tokenInfo.getFamilyName() : "User";

        User user = User.builder()
                .accountId(account.getId())
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .image(tokenInfo.getPicture())
                .createdAt(Instant.now())
                .build();

        userRepository.save(user);
    }

    private void ensureGithubUserProfile(Account account, GithubUserInfo githubUserInfo, String email) {
        String[] splitName = splitName(githubUserInfo.getName());
        String firstName = splitName[0];
        String lastName = splitName[1];

        User existingUser = userRepository.findByAccountId(account.getId()).orElse(null);
        if (existingUser != null) {
            boolean changed = false;

            if (!StringUtils.hasText(existingUser.getImage()) && StringUtils.hasText(githubUserInfo.getAvatarUrl())) {
                existingUser.setImage(githubUserInfo.getAvatarUrl());
                changed = true;
            }
            if (!StringUtils.hasText(existingUser.getFirstName()) && StringUtils.hasText(firstName)) {
                existingUser.setFirstName(firstName);
                changed = true;
            }
            if (!StringUtils.hasText(existingUser.getLastName()) && StringUtils.hasText(lastName)) {
                existingUser.setLastName(lastName);
                changed = true;
            }

            if (changed) {
                userRepository.save(existingUser);
            }
            return;
        }

        User user = User.builder()
                .accountId(account.getId())
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .image(githubUserInfo.getAvatarUrl())
                .createdAt(Instant.now())
                .build();

        userRepository.save(user);
    }

    private String[] splitName(String fullName) {
        if (!StringUtils.hasText(fullName)) {
            return new String[] { "GitHub", "User" };
        }

        String normalized = fullName.trim().replaceAll("\\s+", " ");
        int idx = normalized.lastIndexOf(' ');
        if (idx <= 0 || idx == normalized.length() - 1) {
            return new String[] { normalized, "User" };
        }

        return new String[] { normalized.substring(0, idx), normalized.substring(idx + 1) };
    }

    private String generateUniqueUsername(String email, String fallbackPrefix) {
        String base = email.split("@")[0]
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "");
        if (!StringUtils.hasText(base)) {
            base = fallbackPrefix;
        }

        String candidate = base;
        int suffix = 1;
        while (accountService.existsByUsername(candidate)) {
            candidate = base + suffix;
            suffix++;
        }
        return candidate;
    }

    private String sanitizeUsername(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "");
    }

    private LoginResponseDto buildLoginResponse(Account user, Set<String> roles) {
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
            Set<String> roles = userRolePermissionService.getUserRoles(user.getId());

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

            log.info("Successfully registered user: {} with accountId: {}", savedAccount.getUsername(),
                    savedAccount.getId());

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
            log.error("Unexpected error during registration for user {}: {}", registerRequest.getUsername(),
                    e.getMessage(), e);
            throw new AppException(ErrorCode.REGISTRATION_FAILED);
        }
    }
}
