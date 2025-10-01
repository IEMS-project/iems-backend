package com.iems.projectservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectAllowedRoleDto {
    private UUID id;
    private UUID roleId;
    private String roleName;
}


