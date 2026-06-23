package com.iems.projectservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "subscription_limit_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionLimitSettings {

    @Id
    @Column(name = "plan_type", nullable = false, length = 20)
    private String planType;

    @Column(nullable = false)
    private Integer maxOwnedProjects;

    @Column(nullable = false)
    private Integer maxMembersPerProject;

    @Column(nullable = false)
    private Integer maxIssuesPerProject;

    @Column(nullable = false)
    private Integer maxSprintsPerProject;

    @Column(nullable = false)
    private Integer maxCustomRolesPerProject;

    @Column(nullable = false)
    private Integer activityLogDays;

    @Column(nullable = false)
    private Boolean customWorkflowEnabled;

    @Column(nullable = false)
    private Boolean burndownEnabled;

    @Column(nullable = false)
    private Boolean issueTypePriorityCustomizationEnabled;

    @Column(nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
