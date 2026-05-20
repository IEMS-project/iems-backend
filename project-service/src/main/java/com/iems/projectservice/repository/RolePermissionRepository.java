package com.iems.projectservice.repository;

import com.iems.projectservice.entity.RolePermission;
import com.iems.projectservice.entity.enums.ProjectPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {
    List<RolePermission> findByRoleId(UUID roleId);
    void deleteByRoleIdAndPermission(UUID roleId, ProjectPermission permission);
    boolean existsByRoleIdAndPermission(UUID roleId, ProjectPermission permission);
    void deleteByRoleId(UUID roleId);
    void deleteByRoleIdIn(List<UUID> roleIds);
}
