package com.iems.aiservice.repository;

import com.iems.aiservice.entity.IssueSuggestionVector;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface IssueSuggestionVectorRepository extends MongoRepository<IssueSuggestionVector, String> {
    List<IssueSuggestionVector> findByProjectId(String projectId);

    Optional<IssueSuggestionVector> findByProjectIdAndIssueId(String projectId, String issueId);
}
