package com.iems.projectservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberDto {
    
    @NotNull(message = "Account ID is required")
    private UUID accountId;
    
    @NotNull(message = "Role ID is required")
    private UUID roleId;
}
