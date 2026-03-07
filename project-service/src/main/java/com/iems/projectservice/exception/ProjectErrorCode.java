package com.iems.projectservice.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProjectErrorCode {
    INVALID_REQUEST("Invalid request", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),

    PROJECT_NOT_FOUND("Project not found", HttpStatus.NOT_FOUND),
    PROJECT_NAME_EXISTS("Project name already exists", HttpStatus.CONFLICT),
    MEMBER_NOT_FOUND("Project member not found", HttpStatus.NOT_FOUND),
    PERMISSION_DENIED("Permission denied", HttpStatus.FORBIDDEN),


    PROJECT_MEMBER_ALREADY_EXISTS("User is already a member of this project", HttpStatus.CONFLICT),
    PROJECT_MANAGER_CANNOT_BE_REMOVED("Cannot remove project manager from project", HttpStatus.BAD_REQUEST),
    ROLE_ALREADY_ASSIGNED("Cannot delete role that is already assigned to members", HttpStatus.CONFLICT),
    
    PHASE_NOT_FOUND("Phase not found", HttpStatus.NOT_FOUND),
    PHASE_NOT_BELONG_TO_PROJECT("Phase does not belong to this project", HttpStatus.BAD_REQUEST),


    TASK_NOT_FOUND("Task not found", HttpStatus.NOT_FOUND),
    USER_NOT_FOUND("User not found", HttpStatus.NOT_FOUND),
    INVALID_STATUS_TRANSITION("Invalid status transition", HttpStatus.BAD_REQUEST);

    private final String message;
    private final HttpStatus httpStatus;
}


