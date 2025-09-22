package com.iems.taskservice.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TaskErrorCode {
    INVALID_REQUEST("Invalid request", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),

    TASK_NOT_FOUND("Task not found", HttpStatus.NOT_FOUND),
    USER_NOT_FOUND("User not found", HttpStatus.NOT_FOUND),
    PROJECT_NOT_FOUND("Project not found", HttpStatus.NOT_FOUND),
    PERMISSION_DENIED("Permission denied", HttpStatus.FORBIDDEN),
    INVALID_STATUS_TRANSITION("Invalid status transition", HttpStatus.BAD_REQUEST);

    private final String message;
    private final HttpStatus httpStatus;
}


