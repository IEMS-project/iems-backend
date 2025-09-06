package com.iems.iamservice.repository;

import com.iems.iamservice.entity.Permission;
import com.iems.iamservice.entity.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPermissionRepository extends JpaRepository<UserPermission, UUID> {

    /**
     * Find all permissions assigned directly to a specific user
     */
    @Query("SELECT up.permission FROM UserPermission up WHERE up.userId = :userId AND up.active = true")
    List<Permission> findPermissionsByUserId(@Param("userId") UUID userId);

    /**
     * Find all user-permission assignments for a specific user
     */
    List<UserPermission> findByUserIdAndActiveTrue(UUID userId);

    /**
     * Find specific user-permission assignment
     */
    Optional<UserPermission> findByUserIdAndPermissionIdAndActiveTrue(UUID userId, UUID permissionId);

    /**
     * Check if user has specific permission directly assigned (active only)
     */
    boolean existsByUserIdAndPermissionIdAndActiveTrue(UUID userId, UUID permissionId);

    /**
     * Check if user-permission assignment exists (any status)
     */
    boolean existsByUserIdAndPermissionId(UUID userId, UUID permissionId);

    /**
     * Find all users assigned to a specific permission
     */
    @Query("SELECT up.userId FROM UserPermission up WHERE up.permission.id = :permissionId AND up.active = true")
    List<UUID> findUserIdsByPermissionId(@Param("permissionId") UUID permissionId);

    /**
     * Deactivate user-permission assignment (soft delete)
     */
    @Query("UPDATE UserPermission up SET up.active = false WHERE up.userId = :userId AND up.permission.id = :permissionId")
    void deactivateUserPermission(@Param("userId") UUID userId, @Param("permissionId") UUID permissionId);
    
    /**
     * Find user-permission assignment by user ID and permission ID
     */
    Optional<UserPermission> findByUserIdAndPermissionId(UUID userId, UUID permissionId);
}
