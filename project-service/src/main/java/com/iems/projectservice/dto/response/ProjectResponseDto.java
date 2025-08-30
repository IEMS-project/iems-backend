package com.iems.projectservice.dto.response;

import com.iems.projectservice.dto.request.ProjectProgressDto;
import com.iems.projectservice.entity.enums.ProjectStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponseDto {
    private UUID id;
    private String name;
    private String description;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private ProjectStatus status;
    private UUID managerId;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ProjectMemberResponseDto> members;
    private ProjectProgressDto progress;
}
