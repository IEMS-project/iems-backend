package com.iems.iamservice.controller;

import com.iems.iamservice.dto.ApiResponseDto;
import com.iems.iamservice.dto.request.LoginRequestDto;
import com.iems.iamservice.dto.request.RefreshTokenRequestDto;
import com.iems.iamservice.dto.response.LoginResponseDto;
import com.iems.iamservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Controller handling authentication
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Authentication and token management API")
public class AuthController {

    private final AuthService authService;

    /**
     * Login
     */
    @PostMapping("/login")
    @Operation(summary = "Login", description = "Login with username/email and password")
    public ResponseEntity<ApiResponseDto<LoginResponseDto>> login(@Valid @RequestBody LoginRequestDto loginRequest) {
        log.info("Login request for user: {}", loginRequest.getUsernameOrEmail());
        
        LoginResponseDto response = authService.login(loginRequest);

        // If the service returned a refresh token, store it into an HttpOnly cookie
        try {
            String refreshToken = null;
            try {
                // assume getter exists
                refreshToken = (response != null) ? response.getRefreshToken() : null;
            } catch (Exception ex) {
                // ignore if DTO does not expose getter
            }

            if (refreshToken != null) {
                ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                        .httpOnly(true)
                        .secure(true)
                        .path("/")
                        .maxAge(7 * 24 * 60 * 60) // 7 days
                        .sameSite("Lax")
                        .build();

                // Remove refresh token from response body if possible to avoid exposing it to JS
                try {
                    // if DTO has setter
                    response.getClass().getMethod("setRefreshToken", String.class).invoke(response, (String) null);
                } catch (Exception ignore) {
                    // ignore - best effort only
                }

                return ResponseEntity.ok()
                        .header(HttpHeaders.SET_COOKIE, cookie.toString())
                        .body(ApiResponseDto.<LoginResponseDto>builder()
                                .status("success")
                                .message("Login successful")
                                .data(response)
                                .build());
            }
        } catch (Exception e) {
            log.warn("Failed to set refresh cookie: {}", e.getMessage());
        }

        return ResponseEntity.ok(
                ApiResponseDto.<LoginResponseDto>builder()
                        .status("success")
                        .message("Login successful")
                        .data(response)
                        .build()
        );

    }

    /**
     * Refresh token
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Refresh access token using refresh token")
    public ResponseEntity<ApiResponseDto<LoginResponseDto>> refreshToken(HttpServletRequest request, @RequestBody(required = false) RefreshTokenRequestDto refreshRequest, HttpServletResponse servletResponse) {
        log.info("Refresh token request");

        // Try to read refresh token from cookie first
        String refreshToken = null;
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie c : request.getCookies()) {
                if ("refreshToken".equals(c.getName())) {
                    refreshToken = c.getValue();
                    break;
                }
            }
        }

        if ((refreshToken == null || refreshToken.isEmpty()) && refreshRequest != null) {
            try {
                refreshToken = refreshRequest.getRefreshToken();
            } catch (Exception ignore) {}
        }

        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponseDto.<LoginResponseDto>builder()
                    .status("error")
                    .message("Refresh token is required")
                    .build());
        }

        // Construct a minimal DTO if necessary
        RefreshTokenRequestDto dto = refreshRequest;
        if (dto == null) {
            dto = new RefreshTokenRequestDto();
            try {
                dto.getClass().getMethod("setRefreshToken", String.class).invoke(dto, refreshToken);
            } catch (Exception ignore) {}
        }

        LoginResponseDto response = authService.refreshToken(dto);

        // If a new refresh token is returned, update cookie
        try {
            String newRefresh = null;
            try { newRefresh = (response != null) ? response.getRefreshToken() : null; } catch (Exception ignore) {}
            if (newRefresh != null) {
                ResponseCookie cookie = ResponseCookie.from("refreshToken", newRefresh)
                        .httpOnly(true)
                        .secure(true)
                        .path("/")
                        .maxAge(7 * 24 * 60 * 60)
                        .sameSite("Lax")
                        .build();
                servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
                try { response.getClass().getMethod("setRefreshToken", String.class).invoke(response, (String) null); } catch (Exception ignore) {}
            }
        } catch (Exception e) {
            log.warn("Failed to update refresh cookie: {}", e.getMessage());
        }

        return ResponseEntity.ok(ApiResponseDto.<LoginResponseDto>builder()
                .status("success")
                .message("Token refresh successful")
                .data(response)
                .build());
    }

    /**
     * Logout
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Logout and invalidate token")
    public ResponseEntity<ApiResponseDto<Void>> logout(@RequestHeader(value = "Authorization", required = false) String authHeader, HttpServletRequest request, HttpServletResponse servletResponse) {
        log.info("Logout request");

        // Try to logout by refresh token cookie if present
        String refreshToken = null;
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie c : request.getCookies()) {
                if ("refreshToken".equals(c.getName())) {
                    refreshToken = c.getValue();
                    break;
                }
            }
        }

        try {
            if (refreshToken != null) {
                authService.logout(refreshToken);
            } else if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                authService.logout(token);
            }
        } catch (Exception e) {
            log.warn("Logout processing error: {}", e.getMessage());
        }

        // Clear cookie
        ResponseCookie clear = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, clear.toString());

        return ResponseEntity.ok(ApiResponseDto.<Void>builder()
                .status("success")
                .message("Logout successful")
                .build());
    }

    /**
     * Validate token
     */
    @GetMapping("/validate")
    @Operation(summary = "Validate token", description = "Check token validity")
    public ResponseEntity<ApiResponseDto<Boolean>> validateToken(@RequestHeader("Authorization") String authHeader) {
        log.info("Token validation request");
        
        boolean isValid = false;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            isValid = authService.validateToken(token);
        }
        
        return ResponseEntity.ok(ApiResponseDto.<Boolean>builder()
                .status("success")
                .message(isValid ? "Token is valid" : "Token is invalid")
                .data(isValid)
                .build());
    }

    /**
     * Get user info from token
     */
    @GetMapping("/me")
    @Operation(summary = "Get user info", description = "Get current user information from JWT token")
    public ResponseEntity<ApiResponseDto<LoginResponseDto.UserInfoDto>> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        log.info("Get current user info request");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponseDto.<LoginResponseDto.UserInfoDto>builder()
                            .status("error")
                            .message("Authorization header is required")
                            .build());
        }
        
        String token = authHeader.substring(7);
        LoginResponseDto.UserInfoDto userInfo = authService.getUserInfoFromToken(token);
        
        return ResponseEntity.ok(ApiResponseDto.<LoginResponseDto.UserInfoDto>builder()
                .status("success")
                .message("User information retrieved successfully")
                .data(userInfo)
                .build());
    }
}
