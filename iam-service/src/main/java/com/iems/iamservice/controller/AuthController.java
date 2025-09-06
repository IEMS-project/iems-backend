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
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<ApiResponseDto<LoginResponseDto>> refreshToken(@Valid @RequestBody RefreshTokenRequestDto refreshRequest) {
        log.info("Refresh token request");
        
        LoginResponseDto response = authService.refreshToken(refreshRequest);
        
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
    public ResponseEntity<ApiResponseDto<Void>> logout(@RequestHeader("Authorization") String authHeader) {
        log.info("Logout request");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            authService.logout(token);
        }
        
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
