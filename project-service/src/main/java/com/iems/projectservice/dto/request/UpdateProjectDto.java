package com.iems.projectservice.dto.request;

import com.iems.projectservice.entity.enums.ProjectStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class UpdateProjectDto {
    private String name;
    private String description;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private ProjectStatus status;
    private UUID managerId;
}
