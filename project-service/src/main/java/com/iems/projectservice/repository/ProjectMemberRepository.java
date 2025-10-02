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
    
    Optional<ProjectMember> findByProjectAndUserId(Project project, UUID userId);
    
    List<ProjectMember> findByUserId(UUID userId);
    
    List<ProjectMember> findByProjectAndRoleId(Project project, UUID roleId);
    
    @Query("SELECT pm FROM ProjectMember pm WHERE pm.project.id = :projectId AND pm.userId = :userId")
    Optional<ProjectMember> findMemberByProjectAndUser(@Param("projectId") UUID projectId, 
                                                       @Param("userId") UUID userId);
    
    @Query("SELECT pm FROM ProjectMember pm WHERE pm.project.id = :projectId")
    List<ProjectMember> findByProjectId(@Param("projectId") UUID projectId);
    
    @Query("SELECT pm FROM ProjectMember pm WHERE pm.project.id = :projectId AND pm.userId = :userId")
    Optional<ProjectMember> findByProjectIdAndUserId(@Param("projectId") UUID projectId, 
                                                     @Param("userId") UUID userId);
    
    @Query("SELECT pm FROM ProjectMember pm WHERE pm.project.id = :projectId AND pm.roleId = :roleId")
    List<ProjectMember> findByProjectIdAndRoleId(@Param("projectId") UUID projectId, 
                                                 @Param("roleId") UUID roleId);
    
    @Query("SELECT CASE WHEN COUNT(pm) > 0 THEN true ELSE false END FROM ProjectMember pm WHERE pm.project.id = :projectId AND pm.userId = :userId")
    boolean existsByProjectIdAndUserId(@Param("projectId") UUID projectId, 
                                       @Param("userId") UUID userId);
    
    @Query("DELETE FROM ProjectMember pm WHERE pm.project.id = :projectId AND pm.userId = :userId")
    void deleteByProjectIdAndUserId(@Param("projectId") UUID projectId, 
                                    @Param("userId") UUID userId);
}
