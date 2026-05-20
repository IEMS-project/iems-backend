package com.iems.projectservice.dto.request;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Payload gửi tới notification-service để tạo notification.
 */
@Data
@Builder
public class CreateNotificationRequest {
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

    /** Email của người nhận – notification-service dùng để gửi mail */
    private String recipientEmail;

    /** Tên hiển thị của người nhận – dùng làm greeting trong email */
    private String recipientName;
}
