package com.iems.projectservice.dto.response;

import com.iems.projectservice.entity.enums.ProjectStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MyProjectResponseDto {
    private UUID id;
    private String name;
    private String description;
    private String avatarUrl;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private ProjectStatus status;
    private UUID createdBy;
    private UUID managerId;
    private String managerName;
    private String managerEmail;
    private String managerImage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Double progress;
}


