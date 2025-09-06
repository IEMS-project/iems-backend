package com.iems.iamservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

/**
 * DTO for user permissions list response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPermissionsResponseDto {

    private UUID userId;
    private String username;
    private Set<String> directPermissions;
    private Set<RolePermissionsDto> rolePermissions;
    private Set<String> allPermissions; // Combined all permissions

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RolePermissionsDto {
        private String roleCode;
        private String roleName;
        private Set<String> permissions;
    }
}
