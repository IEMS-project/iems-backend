package com.iems.projectservice.repository;

import com.iems.projectservice.entity.IssueType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IssueTypeRepository extends JpaRepository<IssueType, UUID> {
    List<IssueType> findByProjectIdOrderBySortOrderAsc(UUID projectId);

    long countByProjectId(UUID projectId);
}
