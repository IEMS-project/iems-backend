package com.iems.iamservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * DTO for detailed user permissions information
 * Shows permissions from both roles and direct assignments with source details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPermissionDetails {

    private UUID userId;
    
    /**
     * Roles assigned to the user
     */
    private Set<String> userRoles;
    
    /**
     * Permissions grouped by role
     * Key: role code, Value: set of permission codes
     */
    private Map<String, Set<String>> rolePermissions;
    
    /**
     * Permissions assigned directly to the user
     */
    private Set<String> directPermissions;
    
    /**
     * All permissions the user has (from roles + direct assignments)
     */
    private Set<String> allPermissions;
}
