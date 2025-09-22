package com.iems.userservice.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode {
    INVALID_REQUEST("Invalid request", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),

    USER_NOT_FOUND("User not found", HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_EXISTS("Email already exists", HttpStatus.CONFLICT),
    PERMISSION_DENIED("Permission denied", HttpStatus.FORBIDDEN);

    private final String message;
    private final HttpStatus httpStatus;
}


