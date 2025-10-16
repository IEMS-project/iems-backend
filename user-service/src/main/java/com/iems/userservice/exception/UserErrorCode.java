package com.iems.userservice.exception;

import org.springframework.http.HttpStatus;

public enum UserErrorCode {
    INVALID_REQUEST("Invalid request", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    USER_NOT_FOUND("User not found", HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_EXISTS("Email already exists", HttpStatus.CONFLICT),
    PERMISSION_DENIED("Permission denied", HttpStatus.FORBIDDEN),
    ROLE_NOT_FOUND("Role not found", HttpStatus.NOT_FOUND),
    ROLE_CODE_ALREADY_EXISTS("Role code already exists", HttpStatus.CONFLICT),
    ROLE_NAME_ALREADY_EXISTS("Role name already exists", HttpStatus.CONFLICT);

    private final String message;
    private final HttpStatus httpStatus;

    UserErrorCode(String message, HttpStatus httpStatus) {
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}


