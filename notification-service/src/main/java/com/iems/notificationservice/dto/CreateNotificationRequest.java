package com.iems.notificationservice.dto;

import lombok.Data;

import java.util.UUID;

/**
 * Payload gửi từ project-service → notification-service (internal API).
 */
@Data
public class CreateNotificationRequest {
    private UUID recipientId;
    private UUID actorId;
    private String actorName;

    /** ISSUE_ASSIGNED | ISSUE_COMMENTED | MEMBER_ADDED | SPRINT_STARTED | SPRINT_COMPLETED | ISSUE_DUE_SOON */
    private String type;

    private String title;
    private String body;
    private String entityId;
    private String entityType;
    private UUID projectId;
    private String projectName;
    private String linkPath;

    /** Email address of the recipient – populated by project-service to avoid cross-service lookup */
    private String recipientEmail;

    /** Display name of the recipient – used as greeting in email */
    private String recipientName;
}
