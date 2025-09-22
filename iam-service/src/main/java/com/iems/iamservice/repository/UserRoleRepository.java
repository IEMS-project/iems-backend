package com.iems.iamservice.repository;

import com.iems.iamservice.entity.Role;
import com.iems.iamservice.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    /**
     * Find all roles assigned to a specific user
     */
    @Query("SELECT ur.role FROM UserRole ur WHERE ur.userId = :userId AND ur.active = true")
    List<Role> findRolesByUserId(@Param("userId") UUID userId);

    /**
     * Find all roles with permissions assigned to a specific user
     */
    @Query("SELECT DISTINCT ur.role FROM UserRole ur JOIN FETCH ur.role.permissions WHERE ur.userId = :userId AND ur.active = true")
    List<Role> findRolesWithPermissionsByUserId(@Param("userId") UUID userId);

    /**
     * Find all user-role assignments for a specific user
     */
    List<UserRole> findByUserIdAndActiveTrue(UUID userId);

    /**
     * Find specific user-role assignment
     */
    Optional<UserRole> findByUserIdAndRoleIdAndActiveTrue(UUID userId, UUID roleId);

    /**
     * Check if user has specific role (active only)
     */
    boolean existsByUserIdAndRoleIdAndActiveTrue(UUID userId, UUID roleId);

    /**
     * Check if user-role assignment exists (any status)
     */
    boolean existsByUserIdAndRoleId(UUID userId, UUID roleId);

    /**
     * Find all users assigned to a specific role
     */
    @Query("SELECT ur.userId FROM UserRole ur WHERE ur.role.id = :roleId AND ur.active = true")
    List<UUID> findUserIdsByRoleId(@Param("roleId") UUID roleId);

    /**
     * Deactivate user-role assignment (soft delete)
     */
    @Query("UPDATE UserRole ur SET ur.active = false WHERE ur.userId = :userId AND ur.role.id = :roleId")
    void deactivateUserRole(@Param("userId") UUID userId, @Param("roleId") UUID roleId);
    
    /**
     * Find user-role assignment by user ID and role ID
     */
    Optional<UserRole> findByUserIdAndRoleId(UUID userId, UUID roleId);
}
