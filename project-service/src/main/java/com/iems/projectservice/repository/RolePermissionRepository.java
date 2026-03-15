package com.iems.projectservice.repository;

import com.iems.projectservice.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {
    List<RolePermission> findByRoleId(UUID roleId);
    void deleteByRoleIdAndPermissionId(UUID roleId, UUID permissionId);
    boolean existsByRoleIdAndPermissionId(UUID roleId, UUID permissionId);
    void deleteByRoleId(UUID roleId);
}
