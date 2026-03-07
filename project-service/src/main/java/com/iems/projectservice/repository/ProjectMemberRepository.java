package com.iems.projectservice.repository;

import com.iems.projectservice.entity.Project;
import com.iems.projectservice.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {
    
    List<ProjectMember> findByProject(Project project);
    
    Optional<ProjectMember> findByProjectAndAccountId(Project project, UUID accountId);
    
    List<ProjectMember> findByAccountId(UUID accountId);
    
    List<ProjectMember> findByProjectAndRoleId(Project project, UUID roleId);
    
    @Query("SELECT pm FROM ProjectMember pm WHERE pm.project.id = :projectId AND pm.accountId = :accountId")
    Optional<ProjectMember> findMemberByProjectAndAccount(@Param("projectId") UUID projectId, 
                                                          @Param("accountId") UUID accountId);
    
    @Query("SELECT pm FROM ProjectMember pm WHERE pm.project.id = :projectId")
    List<ProjectMember> findByProjectId(@Param("projectId") UUID projectId);
    
    @Query("SELECT pm FROM ProjectMember pm WHERE pm.project.id = :projectId AND pm.accountId = :accountId")
    Optional<ProjectMember> findByProjectIdAndAccountId(@Param("projectId") UUID projectId, 
                                                        @Param("accountId") UUID accountId);
    
    @Query("SELECT pm FROM ProjectMember pm WHERE pm.project.id = :projectId AND pm.roleId = :roleId")
    List<ProjectMember> findByProjectIdAndRoleId(@Param("projectId") UUID projectId, 
                                                 @Param("roleId") UUID roleId);
    
    @Query("SELECT CASE WHEN COUNT(pm) > 0 THEN true ELSE false END FROM ProjectMember pm WHERE pm.project.id = :projectId AND pm.accountId = :accountId")
    boolean existsByProjectIdAndAccountId(@Param("projectId") UUID projectId, 
                                          @Param("accountId") UUID accountId);
    
    @Query("DELETE FROM ProjectMember pm WHERE pm.project.id = :projectId AND pm.accountId = :accountId")
    void deleteByProjectIdAndAccountId(@Param("projectId") UUID projectId, 
                                       @Param("accountId") UUID accountId);
}
