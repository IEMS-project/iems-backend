package com.iems.projectservice.repository;

import com.iems.projectservice.entity.Phase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PhaseRepository extends JpaRepository<Phase, UUID> {
    
    List<Phase> findByProjectIdOrderBySortOrderAsc(UUID projectId);
    
    List<Phase> findByProjectIdIn(List<UUID> projectIds);
    
    Optional<Phase> findByIdAndProjectId(UUID id, UUID projectId);
    
    @Query("SELECT COALESCE(MAX(p.sortOrder), 0) FROM Phase p WHERE p.project.id = :projectId")
    Integer findMaxSortOrderByProjectId(@Param("projectId") UUID projectId);
    
    void deleteByProjectId(UUID projectId);
    
    boolean existsByIdAndProjectId(UUID id, UUID projectId);
}
