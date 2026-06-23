package com.iems.projectservice.dto.request;

import com.iems.projectservice.entity.enums.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateProjectDto {
    @NotBlank
    private String name;
    
    @NotBlank
    private String projectKey;
    
    private String description;
    
    @NotNull
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private ProjectStatus status;
}
