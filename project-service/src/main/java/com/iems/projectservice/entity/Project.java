package com.iems.projectservice.entity;

import com.iems.projectservice.entity.enums.ProjectStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "project_key", nullable = false, unique = true, length = 10)
    private String projectKey;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProjectStatus status = ProjectStatus.PLANNING;

    @Column(name = "manager_account_id", nullable = false)
    private UUID managerAccountId;

    @Column(name = "created_by_account_id", nullable = false)
    private UUID createdByAccountId;

    /**
     * Cached subscription type of the project owner (manager).
     * Populated when the project is created and updated when the manager upgrades/downgrades.
     * Values: "FREE" | "PREMIUM"
     */
    @Column(name = "owner_subscription", length = 20, nullable = false)
    private String ownerSubscription = "FREE";

    /**
     * When true the project is in read-only mode because the owner's premium expired
     * and the project violates FREE-tier limits.
     */
    @Column(name = "locked", nullable = false)
    private boolean locked = false;

    @Column(name = "lock_reason", length = 500)
    private String lockReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
