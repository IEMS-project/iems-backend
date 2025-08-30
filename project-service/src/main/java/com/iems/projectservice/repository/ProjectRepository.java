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
    
    List<Project> findByManagerId(UUID managerId);
    
    List<Project> findByStatus(ProjectStatus status);
    
    @Query("SELECT p FROM Project p WHERE p.startDate BETWEEN :startDate AND :endDate")
    List<Project> findByDateRange(@Param("startDate") LocalDateTime startDate, 
                                  @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT p FROM Project p JOIN p.members pm WHERE pm.userId = :userId")
    List<Project> findByMemberId(@Param("userId") UUID userId);
    
    boolean existsByName(String name);
}
