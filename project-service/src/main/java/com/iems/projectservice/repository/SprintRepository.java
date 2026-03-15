package com.iems.projectservice.repository;

import com.iems.projectservice.entity.Sprint;
import com.iems.projectservice.entity.enums.SprintStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SprintRepository extends JpaRepository<Sprint, UUID> {
    List<Sprint> findByProjectIdOrderBySortOrderAsc(UUID projectId);
    Optional<Sprint> findByProjectIdAndStatus(UUID projectId, SprintStatus status);
    boolean existsByProjectIdAndStatus(UUID projectId, SprintStatus status);
}
