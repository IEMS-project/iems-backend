package com.iems.iamservice.service;

import com.iems.iamservice.dto.response.UserPermissionDetails;
import com.iems.iamservice.entity.Permission;
import com.iems.iamservice.entity.Role;
import com.iems.iamservice.entity.UserPermission;
import com.iems.iamservice.entity.UserRole;
import com.iems.iamservice.repository.PermissionRepository;
import com.iems.iamservice.repository.RoleRepository;
import com.iems.iamservice.repository.UserPermissionRepository;
import com.iems.iamservice.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserRolePermissionService {

    private final UserRoleRepository userRoleRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    /**
     * Assign roles to user (additive - allows multiple roles, no error for duplicates)
     */
    @Transactional
    public void assignRolesToUser(UUID userId, Set<String> roleCodes) {
        log.info("Assigning roles {} to user ID: {}", roleCodes, userId);

        // Add new role assignments (skip if already exists)
        for (String roleCode : roleCodes) {
            Role role = roleRepository.findByCode(roleCode)
                    .orElseThrow(() -> new RuntimeException("Role not found with code: " + roleCode));

            // Check if user-role assignment already exists (any status)
            boolean alreadyExists = userRoleRepository.existsByUserIdAndRoleId(userId, role.getId());
            
            if (!alreadyExists) {
                UserRole userRole = UserRole.builder()
                        .userId(userId)
                        .role(role)
                        .active(true)
                        .build();

                userRoleRepository.save(userRole);
                log.info("Role {} assigned to user ID: {}", roleCode, userId);
            } else {
                // Check if the existing assignment is inactive, if so, reactivate it
                Optional<UserRole> existingUserRole = userRoleRepository.findByUserIdAndRoleId(userId, role.getId());
                if (existingUserRole.isPresent() && !existingUserRole.get().getActive()) {
                    existingUserRole.get().setActive(true);
                    userRoleRepository.save(existingUserRole.get());
                    log.info("Role {} reactivated for user ID: {}", roleCode, userId);
                } else {
                    log.info("User ID: {} already has active role: {}, skipping", userId, roleCode);
                }
            }
        }

        log.info("Roles assignment completed for user ID: {}", userId);
    }

    /**
     * Replace all roles for user (removes existing roles and assigns new ones)
     */
    @Transactional
    public void replaceUserRoles(UUID userId, Set<String> roleCodes) {
        log.info("Replacing roles {} for user ID: {}", roleCodes, userId);

        // Deactivate existing role assignments
        userRoleRepository.findByUserIdAndActiveTrue(userId)
                .forEach(userRole -> {
                    userRole.setActive(false);
                    userRoleRepository.save(userRole);
                });

        // Add new role assignments
        for (String roleCode : roleCodes) {
            Role role = roleRepository.findByCode(roleCode)
                    .orElseThrow(() -> new RuntimeException("Role not found with code: " + roleCode));

            UserRole userRole = UserRole.builder()
                    .userId(userId)
                    .role(role)
                    .active(true)
                    .build();

            userRoleRepository.save(userRole);
        }

        log.info("Roles replaced successfully for user ID: {}", userId);
    }

    /**
     * Assign permissions directly to user (additive - allows multiple permissions, no error for duplicates)
     */
    @Transactional
    public void assignPermissionsToUser(UUID userId, Set<String> permissionCodes) {
        log.info("Assigning permissions {} to user ID: {}", permissionCodes, userId);

        // Add new permission assignments (skip if already exists)
        for (String permissionCode : permissionCodes) {
            Permission permission = permissionRepository.findByCode(permissionCode)
                    .orElseThrow(() -> new RuntimeException("Permission not found with code: " + permissionCode));

            // Check if user-permission assignment already exists (any status)
            boolean alreadyExists = userPermissionRepository.existsByUserIdAndPermissionId(userId, permission.getId());
            
            if (!alreadyExists) {
                UserPermission userPermission = UserPermission.builder()
                        .userId(userId)
                        .permission(permission)
                        .active(true)
                        .build();

                userPermissionRepository.save(userPermission);
                log.info("Permission {} assigned to user ID: {}", permissionCode, userId);
            } else {
                // Check if the existing assignment is inactive, if so, reactivate it
                Optional<UserPermission> existingUserPermission = userPermissionRepository.findByUserIdAndPermissionId(userId, permission.getId());
                if (existingUserPermission.isPresent() && !existingUserPermission.get().getActive()) {
                    existingUserPermission.get().setActive(true);
                    userPermissionRepository.save(existingUserPermission.get());
                    log.info("Permission {} reactivated for user ID: {}", permissionCode, userId);
                } else {
                    log.info("User ID: {} already has active permission: {}, skipping", userId, permissionCode);
                }
            }
        }

        log.info("Permissions assignment completed for user ID: {}", userId);
    }

    /**
     * Replace all direct permissions for user (removes existing permissions and assigns new ones)
     */
    @Transactional
    public void replaceUserPermissions(UUID userId, Set<String> permissionCodes) {
        log.info("Replacing permissions {} for user ID: {}", permissionCodes, userId);

        // Deactivate existing direct permission assignments
        userPermissionRepository.findByUserIdAndActiveTrue(userId)
                .forEach(userPermission -> {
                    userPermission.setActive(false);
                    userPermissionRepository.save(userPermission);
                });

        // Add new permission assignments
        for (String permissionCode : permissionCodes) {
            Permission permission = permissionRepository.findByCode(permissionCode)
                    .orElseThrow(() -> new RuntimeException("Permission not found with code: " + permissionCode));

            UserPermission userPermission = UserPermission.builder()
                    .userId(userId)
                    .permission(permission)
                    .active(true)
                    .build();

            userPermissionRepository.save(userPermission);
        }

        log.info("Permissions replaced successfully for user ID: {}", userId);
    }

    /**
     * Get all roles assigned to user
     */
    public Set<Role> getUserRoles(UUID userId) {
        return new HashSet<>(userRoleRepository.findRolesByUserId(userId));
    }

    /**
     * Get all permissions assigned directly to user
     */
    public Set<Permission> getUserDirectPermissions(UUID userId) {
        return new HashSet<>(userPermissionRepository.findPermissionsByUserId(userId));
    }

    /**
     * Get all permissions for user (from roles + direct assignments)
     */
    public Set<Permission> getAllUserPermissions(UUID userId) {
        // Get permissions from active roles only
        Set<Role> userRoles = getUserRoles(userId);
        Set<Permission> rolePermissions = userRoles.stream()
                .filter(role -> role.getActive()) // Only active roles
                .flatMap(role -> role.getPermissions().stream())
                .filter(permission -> permission.getActive()) // Only active permissions
                .collect(Collectors.toSet());

        // Get direct permissions (already filtered by active in repository)
        Set<Permission> directPermissions = getUserDirectPermissions(userId);

        // Combine and return unique permissions
        rolePermissions.addAll(directPermissions);
        return rolePermissions;
    }

    /**
     * Check if user has specific role
     */
    public boolean userHasRole(UUID userId, String roleCode) {
        Optional<Role> roleOpt = roleRepository.findByCode(roleCode);
        if (roleOpt.isEmpty() || !roleOpt.get().getActive()) {
            return false;
        }
        
        return userRoleRepository.existsByUserIdAndRoleIdAndActiveTrue(
                userId, 
                roleOpt.get().getId()
        );
    }

    /**
     * Check if user has specific permission
     */
    public boolean userHasPermission(UUID userId, String permissionCode) {
        // Check direct permission
        boolean hasDirectPermission = userPermissionRepository.existsByUserIdAndPermissionIdAndActiveTrue(
                userId,
                permissionRepository.findByCode(permissionCode)
                        .map(Permission::getId)
                        .orElse(null)
        );

        if (hasDirectPermission) {
            return true;
        }

        // Check permission through active roles only
        Set<Role> userRoles = getUserRoles(userId);
        return userRoles.stream()
                .filter(role -> role.getActive()) // Only active roles
                .flatMap(role -> role.getPermissions().stream())
                .filter(permission -> permission.getActive()) // Only active permissions
                .anyMatch(permission -> permission.getCode().equals(permissionCode));
    }

    /**
     * Remove role from user
     */
    @Transactional
    public void removeRoleFromUser(UUID userId, String roleCode) {
        log.info("Removing role {} from user ID: {}", roleCode, userId);

        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new RuntimeException("Role not found with code: " + roleCode));

        userRoleRepository.deactivateUserRole(userId, role.getId());
        log.info("Role removed successfully from user ID: {}", userId);
    }

    /**
     * Remove permission from user
     */
    @Transactional
    public void removePermissionFromUser(UUID userId, String permissionCode) {
        log.info("Removing permission {} from user ID: {}", permissionCode, userId);

        Permission permission = permissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new RuntimeException("Permission not found with code: " + permissionCode));

        userPermissionRepository.deactivateUserPermission(userId, permission.getId());
        log.info("Permission removed successfully from user ID: {}", userId);
    }

    /**
     * Get all users assigned to a role
     */
    public List<UUID> getUsersByRole(String roleCode) {
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new RuntimeException("Role not found with code: " + roleCode));

        return userRoleRepository.findUserIdsByRoleId(role.getId());
    }

    /**
     * Get all users assigned to a permission
     */
    public List<UUID> getUsersByPermission(String permissionCode) {
        Permission permission = permissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new RuntimeException("Permission not found with code: " + permissionCode));

        return userPermissionRepository.findUserIdsByPermissionId(permission.getId());
    }

    /**
     * Get detailed user permissions with source information
     * Returns permissions from both roles and direct assignments with source details
     */
    public UserPermissionDetails getUserPermissionDetails(UUID userId) {
        log.info("Getting detailed permissions for user ID: {}", userId);

        // Get user roles
        Set<Role> userRoles = getUserRoles(userId);
        
        // Get direct permissions
        Set<Permission> directPermissions = getUserDirectPermissions(userId);
        
        // Get all permissions (combined)
        Set<Permission> allPermissions = getAllUserPermissions(userId);
        
        // Build role-based permissions map
        Map<String, Set<String>> rolePermissions = userRoles.stream()
                .filter(role -> role.getActive())
                .collect(Collectors.toMap(
                        Role::getCode,
                        role -> role.getPermissions().stream()
                                .filter(permission -> permission.getActive())
                                .map(Permission::getCode)
                                .collect(Collectors.toSet())
                ));
        
        // Build direct permissions set
        Set<String> directPermissionCodes = directPermissions.stream()
                .map(Permission::getCode)
                .collect(Collectors.toSet());
        
        // Build all permissions set
        Set<String> allPermissionCodes = allPermissions.stream()
                .map(Permission::getCode)
                .collect(Collectors.toSet());

        return UserPermissionDetails.builder()
                .userId(userId)
                .userRoles(userRoles.stream()
                        .filter(role -> role.getActive())
                        .map(Role::getCode)
                        .collect(Collectors.toSet()))
                .rolePermissions(rolePermissions)
                .directPermissions(directPermissionCodes)
                .allPermissions(allPermissionCodes)
                .build();
    }
}
