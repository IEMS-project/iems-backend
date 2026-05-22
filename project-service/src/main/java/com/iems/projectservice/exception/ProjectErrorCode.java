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
    PROJECT_KEY_EXISTS("Project key already exists", HttpStatus.CONFLICT),
    PERMISSION_DENIED("Permission denied", HttpStatus.FORBIDDEN),

    MEMBER_NOT_FOUND("Project member not found", HttpStatus.NOT_FOUND),
    PROJECT_MEMBER_ALREADY_EXISTS("User is already a member of this project", HttpStatus.CONFLICT),
    PROJECT_MANAGER_CANNOT_BE_REMOVED("Cannot remove project manager from project", HttpStatus.BAD_REQUEST),

    ROLE_NOT_FOUND("Role not found", HttpStatus.NOT_FOUND),
    ROLE_ALREADY_EXISTS("Role already exists in this project", HttpStatus.CONFLICT),
    ROLE_ALREADY_ASSIGNED("Cannot delete role that is already assigned to members", HttpStatus.CONFLICT),
    DEFAULT_ROLE_PERMISSIONS_LOCKED("Cannot modify permissions of the admin/default role", HttpStatus.BAD_REQUEST),
    DEFAULT_ROLE_MEMBER_PERMISSIONS_LOCKED("Cannot modify user permissions for a member with the admin/default role",
            HttpStatus.BAD_REQUEST),

    PERMISSION_NOT_FOUND("Permission not found", HttpStatus.NOT_FOUND),
    PERMISSION_ALREADY_ASSIGNED("Permission is already assigned to this role", HttpStatus.CONFLICT),

    WORKFLOW_NOT_FOUND("Workflow not found", HttpStatus.NOT_FOUND),
    WORKFLOW_STATUS_NOT_FOUND("Workflow status not found", HttpStatus.NOT_FOUND),
    WORKFLOW_TRANSITION_NOT_FOUND("Workflow transition not found", HttpStatus.NOT_FOUND),
    INVALID_WORKFLOW_TRANSITION("Invalid workflow transition", HttpStatus.BAD_REQUEST),

    ISSUE_NOT_FOUND("Issue not found", HttpStatus.NOT_FOUND),
    ISSUE_TYPE_NOT_FOUND("Issue type not found", HttpStatus.NOT_FOUND),
    ISSUE_PRIORITY_NOT_FOUND("Issue priority not found", HttpStatus.NOT_FOUND),
    ISSUE_IMPORT_FILE_INVALID("Issue import file is invalid", HttpStatus.BAD_REQUEST),
    ISSUE_IMPORT_TEMPLATE_INVALID("Issue import template is invalid", HttpStatus.BAD_REQUEST),
    ISSUE_IMPORT_ROW_INVALID("Issue import row is invalid", HttpStatus.BAD_REQUEST),

    SPRINT_NOT_FOUND("Sprint not found", HttpStatus.NOT_FOUND),
    SPRINT_ALREADY_ACTIVE("A sprint is already active in this project", HttpStatus.CONFLICT),
    SPRINT_NOT_ACTIVE("Sprint is not active", HttpStatus.BAD_REQUEST),

    COMMENT_NOT_FOUND("Comment not found", HttpStatus.NOT_FOUND),
    ATTACHMENT_NOT_FOUND("Attachment not found", HttpStatus.NOT_FOUND),

    USER_NOT_FOUND("User not found", HttpStatus.NOT_FOUND),

    // Subscription & Limit Errors
    SUBSCRIPTION_LIMIT_SETTINGS_NOT_FOUND("Subscription limit settings not found", HttpStatus.NOT_FOUND),
    PROJECT_LIMIT_EXCEEDED("You have reached the maximum number of projects for your plan", HttpStatus.PAYMENT_REQUIRED),
    MEMBER_LIMIT_EXCEEDED("This project has reached the maximum number of members for its plan", HttpStatus.PAYMENT_REQUIRED),
    ISSUE_LIMIT_EXCEEDED("This project has reached the maximum number of issues for its plan", HttpStatus.PAYMENT_REQUIRED),
    SPRINT_LIMIT_EXCEEDED("This project has reached the maximum number of sprints for its plan", HttpStatus.PAYMENT_REQUIRED),
    PREMIUM_REQUIRED("This feature requires a Premium subscription", HttpStatus.PAYMENT_REQUIRED),
    PROJECT_LOCKED("This project is locked due to an expired premium subscription", HttpStatus.FORBIDDEN);

    private final String message;
    private final HttpStatus httpStatus;
}
