package com.iems.projectservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberResponseDto {
    private UUID id;
    private UUID userId;
    private String userName;
    private String userEmail;
    private String userImage;
    private UUID roleId;
    private String roleName; // For display purposes
    private LocalDateTime joinedAt;
    private UUID assignedBy;
    private String assignedByName;
}
