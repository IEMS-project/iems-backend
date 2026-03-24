package com.iems.documentservice.repository;

import com.iems.documentservice.entity.ProjectDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, UUID> {

    List<ProjectDocument> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    Optional<ProjectDocument> findByProjectIdAndId(UUID projectId, UUID id);

    List<ProjectDocument> findByProjectIdAndParentId(UUID projectId, UUID parentId);
}
