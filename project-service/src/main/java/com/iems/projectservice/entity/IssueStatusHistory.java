package com.iems.projectservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "issue_status_histories")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IssueStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "issue_id", nullable = false)
    private UUID issueId;

    @Column(name = "sprint_id")
    private UUID sprintId;

    @Column(name = "from_status_id")
    private UUID fromStatusId;

    @Column(name = "to_status_id", nullable = false)
    private UUID toStatusId;

    @Column(name = "story_points")
    private Integer storyPoints;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;
}
