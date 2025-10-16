package com.iems.iamservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * DTO for role permissions list response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissionsResponseDto {

    private UUID roleId;
    private String roleCode;
    private String roleName;
    private String description;
    private Boolean active;
    private Instant createdAt;
    private Set<String> permissions;
}
