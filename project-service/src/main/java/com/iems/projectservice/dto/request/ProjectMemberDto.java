package com.iems.projectservice.dto.request;

import com.iems.projectservice.entity.enums.ProjectRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberDto {
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @NotNull(message = "Project role is required")
    private ProjectRole role;
    
    @NotNull(message = "Assigned by user ID is required")
    private UUID assignedBy;
}
