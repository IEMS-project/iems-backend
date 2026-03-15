package com.iems.projectservice.repository;

import com.iems.projectservice.entity.IssuePriority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IssuePriorityRepository extends JpaRepository<IssuePriority, UUID> {
    List<IssuePriority> findByProjectIdOrderBySortOrderAsc(UUID projectId);
}
