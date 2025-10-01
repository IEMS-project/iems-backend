package com.iems.projectservice.repository;

import com.iems.projectservice.entity.ProjectAllowedRole;
import com.iems.projectservice.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectAllowedRoleRepository extends JpaRepository<ProjectAllowedRole, UUID> {
    List<ProjectAllowedRole> findByProject(Project project);
    Optional<ProjectAllowedRole> findByProjectAndRoleId(Project project, UUID roleId);
}


