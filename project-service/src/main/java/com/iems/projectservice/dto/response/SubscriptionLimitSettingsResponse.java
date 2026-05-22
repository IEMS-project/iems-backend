package com.iems.projectservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class SubscriptionLimitSettingsResponse {
    private String planType;
    private Integer maxOwnedProjects;
    private Integer maxMembersPerProject;
    private Integer maxIssuesPerProject;
    private Integer maxSprintsPerProject;
    private Integer maxCustomRolesPerProject;
    private Integer activityLogDays;
    private Boolean customWorkflowEnabled;
    private Boolean burndownEnabled;
    private Boolean issueTypePriorityCustomizationEnabled;
    private Instant createdAt;
    private Instant updatedAt;
}
