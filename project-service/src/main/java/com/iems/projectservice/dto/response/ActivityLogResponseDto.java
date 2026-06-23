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
}
