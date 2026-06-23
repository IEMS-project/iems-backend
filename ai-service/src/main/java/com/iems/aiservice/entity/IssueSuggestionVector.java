package com.iems.aiservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "issue_suggestion_vectors")
@CompoundIndex(name = "project_issue_unique", def = "{'projectId': 1, 'issueId': 1}", unique = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IssueSuggestionVector {
    @Id
    private String id;
    private String projectId;
    private String issueId;
    private String text;
    private List<Double> embedding;
    private Instant updatedAt;
}
