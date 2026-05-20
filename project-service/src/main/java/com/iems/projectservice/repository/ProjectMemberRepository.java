package com.iems.projectservice.repository;

import com.iems.projectservice.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {
    
    List<ProjectMember> findByProjectId(UUID projectId);
    
    Optional<ProjectMember> findByProjectIdAndAccountId(UUID projectId, UUID accountId);
    
    List<ProjectMember> findByAccountId(UUID accountId);
    
    List<ProjectMember> findByProjectIdAndRoleId(UUID projectId, UUID roleId);
    
    boolean existsByProjectIdAndAccountId(UUID projectId, UUID accountId);
    
    void deleteByProjectIdAndAccountId(UUID projectId, UUID accountId);

    void deleteByProjectId(UUID projectId);

    /** Count how many members a project currently has. */
    long countByProjectId(UUID projectId);
}
