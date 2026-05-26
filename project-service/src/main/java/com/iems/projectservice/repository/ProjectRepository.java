package com.iems.projectservice.repository;

import com.iems.projectservice.entity.Project;
import com.iems.projectservice.entity.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    
    Optional<Project> findByName(String name);
    
    Optional<Project> findByProjectKey(String projectKey);
    
    List<Project> findByManagerAccountId(UUID managerAccountId);
    
    List<Project> findByStatus(ProjectStatus status);
    
    @Query("SELECT p FROM Project p WHERE p.startDate BETWEEN :startDate AND :endDate")
    List<Project> findByDateRange(@Param("startDate") LocalDateTime startDate, 
                                  @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT DISTINCT p FROM Project p JOIN ProjectMember pm ON pm.projectId = p.id WHERE pm.accountId = :accountId AND pm.status = com.iems.projectservice.entity.enums.MemberStatus.ACTIVE")
    List<Project> findByMemberAccountId(@Param("accountId") UUID accountId);

        @Query(value = "SELECT DISTINCT p FROM Project p LEFT JOIN ProjectMember pm ON pm.projectId = p.id " +
            "WHERE p.managerAccountId = :accountId OR (pm.accountId = :accountId AND pm.status = com.iems.projectservice.entity.enums.MemberStatus.ACTIVE)",
            countQuery = "SELECT COUNT(DISTINCT p.id) FROM Project p LEFT JOIN ProjectMember pm ON pm.projectId = p.id " +
                "WHERE p.managerAccountId = :accountId OR (pm.accountId = :accountId AND pm.status = com.iems.projectservice.entity.enums.MemberStatus.ACTIVE)")
        Page<Project> findByOwnerOrMember(@Param("accountId") UUID accountId, Pageable pageable);
    
    boolean existsByName(String name);
    
    boolean existsByProjectKey(String projectKey);

    /** Count projects where the given account is the manager (owner). */
    long countByManagerAccountId(UUID managerAccountId);

    /** Find locked projects owned by a given manager (for scheduled lock checks). */
    List<Project> findByManagerAccountIdAndLocked(UUID managerAccountId, boolean locked);
}
