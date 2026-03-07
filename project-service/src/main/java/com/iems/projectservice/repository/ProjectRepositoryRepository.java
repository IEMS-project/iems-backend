package com.iems.projectservice.repository;

import com.iems.projectservice.entity.GithubRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectRepositoryRepository extends JpaRepository<GithubRepository, UUID> {
    List<GithubRepository> findByProjectId(UUID projectId);
    void deleteByProjectId(UUID projectId);
}
