package com.iems.notificationservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notification_recipient", columnList = "recipient_id"),
    @Index(name = "idx_notification_created", columnList = "created_at"),
    @Index(name = "idx_notification_read", columnList = "recipient_id,is_read")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_name", length = 100)
    private String actorName;

    /** ISSUE_ASSIGNED | ISSUE_COMMENTED | MEMBER_ADDED | SPRINT_STARTED | SPRINT_COMPLETED */
    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String body;

    /** ID of the entity (issueId / sprintId / projectId) */
    @Column(name = "entity_id", length = 100)
    private String entityId;

    /** ISSUE | SPRINT | PROJECT */
    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "project_name", length = 200)
    private String projectName;

    /** Deep link path on frontend e.g. /projects/{id}/backlog */
    @Column(name = "link_path", length = 500)
    private String linkPath;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
