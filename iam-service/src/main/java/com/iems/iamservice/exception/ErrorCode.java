package com.iems.iamservice.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // Authentication and Authorization Errors
    UNAUTHENTICATED("Unauthenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED("Forbidden", HttpStatus.FORBIDDEN),
    INVALID_TOKEN("Invalid token", HttpStatus.BAD_REQUEST),
    EXPIRED_TOKEN("Token expired", HttpStatus.BAD_REQUEST),
    INVALID_REQUEST("Invalid request", HttpStatus.BAD_REQUEST),
    EMAIL_EXIST_REGISTER("Email already in use", HttpStatus.CONFLICT),
    USERNAME_EXIST_REGISTER("Username already in use", HttpStatus.CONFLICT),
    INVALID_SIGNUP_DATA("Invalid sign-up data", HttpStatus.BAD_REQUEST),

    // User Management Errors
    USER_NOT_EXIST("User not found", HttpStatus.NOT_FOUND),
    USER_NOT_FOUND_BY_EMAIL("User not found", HttpStatus.NOT_FOUND),

    // OTP and Password Reset Errors
    INVALID_OTP("Invalid OTP", HttpStatus.BAD_REQUEST),
    OTP_NOT_FOUND("OTP not found", HttpStatus.NOT_FOUND),

    // Refresh Token Errors
    REFRESH_TOKEN_NOT_FOUND("Refresh token not found", HttpStatus.BAD_REQUEST),
    REFRESH_TOKEN_EXPIRED("Refresh token expired", HttpStatus.BAD_REQUEST),

    // Logout Errors
    LOGOUT_FAILED("Logout failed", HttpStatus.UNAUTHORIZED),

    // Email Related Errors
    EMAIL_INVALID("Invalid email address", HttpStatus.BAD_REQUEST),
    EMAIL_SEND_FAILED("Email sending failed", HttpStatus.INTERNAL_SERVER_ERROR),

    // Profile, Address, and Album Errors
    PROFILE_NOT_FOUND("Profile not found", HttpStatus.NOT_FOUND),
    ADDRESS_NOT_FOUND("Address not found", HttpStatus.NOT_FOUND),
    ALBUM_NOT_FOUND("Album not found", HttpStatus.NOT_FOUND),

    // Authentication Service Errors
    LOGIN_FAILED("Login failed", HttpStatus.UNAUTHORIZED),
    REGISTRATION_FAILED("Registration failed", HttpStatus.INTERNAL_SERVER_ERROR),
    ACCOUNT_LOCKED("Account is locked", HttpStatus.FORBIDDEN),
    INVALID_CREDENTIALS("Invalid credentials", HttpStatus.UNAUTHORIZED),
    TOKEN_VALIDATION_FAILED("Token validation failed", HttpStatus.UNAUTHORIZED),
    USER_NOT_FOUND_IN_TOKEN("User not found in token", HttpStatus.UNAUTHORIZED),

    // Account Service Errors
    USERNAME_ALREADY_EXISTS("Username already exists", HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS("Email already exists", HttpStatus.CONFLICT),
    USER_NOT_FOUND_BY_ID("User not found", HttpStatus.NOT_FOUND),
    USER_UPDATE_FAILED("User update failed", HttpStatus.INTERNAL_SERVER_ERROR),
    USER_LOCK_FAILED("User lock/unlock failed", HttpStatus.INTERNAL_SERVER_ERROR),
    USER_DELETE_FAILED("User deletion failed", HttpStatus.INTERNAL_SERVER_ERROR),

    // Role Service Errors
    ROLE_CODE_ALREADY_EXISTS("Role code already exists", HttpStatus.CONFLICT),
    ROLE_NOT_FOUND_BY_ID("Role not found", HttpStatus.NOT_FOUND),
    ROLE_NOT_FOUND_BY_CODE("Role not found", HttpStatus.NOT_FOUND),
    ROLE_UPDATE_FAILED("Role update failed", HttpStatus.INTERNAL_SERVER_ERROR),
    ROLE_DELETE_FAILED("Role deletion failed", HttpStatus.INTERNAL_SERVER_ERROR),
    ROLE_IN_USE("Role is in use", HttpStatus.CONFLICT),

    // User Role Service Errors
    USER_ROLE_ASSIGNMENT_FAILED("Assigning role to user failed", HttpStatus.INTERNAL_SERVER_ERROR),
    USER_ROLE_REMOVAL_FAILED("Removing role from user failed", HttpStatus.INTERNAL_SERVER_ERROR),
    ROLE_REPLACEMENT_FAILED("Replacing user's roles failed", HttpStatus.INTERNAL_SERVER_ERROR),

    // System Errors
    INTERNAL_SERVER_ERROR("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_ERROR("Database error", HttpStatus.INTERNAL_SERVER_ERROR),
    EXTERNAL_SERVICE_ERROR("External service error", HttpStatus.SERVICE_UNAVAILABLE);

    private final String message;
    private final HttpStatus httpStatus;
}