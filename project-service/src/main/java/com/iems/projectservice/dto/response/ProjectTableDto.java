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
public class ProjectTableDto {
    private UUID id;
    private String name;
    private String description;
    private ProjectStatus status;
    private UUID managerId;
    private String managerName;
    private String managerEmail;
    private String managerImage;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
