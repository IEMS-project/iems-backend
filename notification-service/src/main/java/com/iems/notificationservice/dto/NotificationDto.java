package com.iems.notificationservice.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class NotificationDto {
    private UUID id;
    private UUID recipientId;
    private UUID actorId;
    private String actorName;
    private String type;
    private String title;
    private String body;
    private String entityId;
    private String entityType;
    private UUID projectId;
    private String projectName;
    private String linkPath;
    private boolean read;
    private Instant createdAt;
}
