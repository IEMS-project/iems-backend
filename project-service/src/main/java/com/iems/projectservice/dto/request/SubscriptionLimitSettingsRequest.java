package com.iems.projectservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubscriptionLimitSettingsRequest {
    @NotNull
    @Min(0)
    private Integer maxOwnedProjects;

    @NotNull
    @Min(0)
    private Integer maxMembersPerProject;

    @NotNull
    @Min(0)
    private Integer maxIssuesPerProject;

    @NotNull
    @Min(0)
    private Integer maxSprintsPerProject;

    @NotNull
    @Min(0)
    private Integer maxCustomRolesPerProject;

    @NotNull
    @Min(0)
    private Integer activityLogDays;

    @NotNull
    private Boolean customWorkflowEnabled;

    @NotNull
    private Boolean burndownEnabled;

    @NotNull
    private Boolean issueTypePriorityCustomizationEnabled;
}
