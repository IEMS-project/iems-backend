package com.iems.projectservice.repository;

import com.iems.projectservice.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    List<Role> findByProjectId(UUID projectId);
    Optional<Role> findByProjectIdAndName(UUID projectId, String name);
    Optional<Role> findByProjectIdAndIsDefaultTrue(UUID projectId);
    boolean existsByProjectIdAndName(UUID projectId, String name);
}
