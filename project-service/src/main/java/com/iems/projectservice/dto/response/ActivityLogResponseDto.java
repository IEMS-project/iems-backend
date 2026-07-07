package com.iems.projectservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLogResponseDto {
    private UUID id;
    private UUID projectId;
    private UUID issueId;
    private UUID userId;
    private String userName;
    private String userImage;
    private String action;
    private String details;
    private LocalDateTime createdAt;
    private String projectName;

    public ActivityLogResponseDto(UUID id, UUID projectId, UUID issueId, UUID userId, String userName,
            String userImage, String action, String details, LocalDateTime createdAt) {
        this.id = id;
        this.projectId = projectId;
        this.issueId = issueId;
        this.userId = userId;
        this.userName = userName;
        this.userImage = userImage;
        this.action = action;
        this.details = details;
        this.createdAt = createdAt;
    }
}
