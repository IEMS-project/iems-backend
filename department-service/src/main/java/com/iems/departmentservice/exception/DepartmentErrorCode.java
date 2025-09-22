package com.iems.departmentservice.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DepartmentErrorCode {
    // Validation & Common
    INVALID_REQUEST("Invalid request", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),

    // Department domain
    DEPARTMENT_NOT_FOUND("Department not found", HttpStatus.NOT_FOUND),
    DEPARTMENT_NAME_EXISTS("Department name already exists", HttpStatus.CONFLICT),
    MANAGER_NOT_FOUND("Manager not found", HttpStatus.NOT_FOUND),
    USER_NOT_IN_DEPARTMENT("User is not in department", HttpStatus.BAD_REQUEST),
    USER_ALREADY_IN_DEPARTMENT("User already in department", HttpStatus.CONFLICT),
    PERMISSION_DENIED("Permission denied", HttpStatus.FORBIDDEN);

    private final String message;
    private final HttpStatus httpStatus;
}


