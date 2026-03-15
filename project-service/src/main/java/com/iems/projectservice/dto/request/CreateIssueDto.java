package com.iems.projectservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateIssueDto {
    @NotNull
    private UUID issueTypeId;
    
    private UUID parentId;
    
    @NotBlank
    private String title;
    
    private String description;
    private UUID priorityId;
    private UUID assigneeId;
    private UUID sprintId;
    private Integer storyPoints;
}
