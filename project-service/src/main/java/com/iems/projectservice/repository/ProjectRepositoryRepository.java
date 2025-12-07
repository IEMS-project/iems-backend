package com.iems.projectservice.repository;

import com.iems.projectservice.entity.ProjectRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectRepositoryRepository extends JpaRepository<ProjectRepository, UUID> {
    List<ProjectRepository> findByProjectId(UUID projectId);
    void deleteByProjectId(UUID projectId);
}
