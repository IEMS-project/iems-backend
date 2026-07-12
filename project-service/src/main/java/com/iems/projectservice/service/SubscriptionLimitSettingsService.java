package com.iems.projectservice.service;

import com.iems.projectservice.dto.request.SubscriptionLimitSettingsRequest;
import com.iems.projectservice.dto.response.SubscriptionLimitSettingsResponse;
import com.iems.projectservice.entity.SubscriptionLimitSettings;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.SubscriptionLimitSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SubscriptionLimitSettingsService {

    private final SubscriptionLimitSettingsRepository repository;

    /**
     * Ensures that subscription limit settings requirements are satisfied.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     */
    @Transactional
    public void ensureDefaults() {
        if (!repository.existsById("FREE")) {
            repository.save(defaultFree());
        }
        if (!repository.existsById("PREMIUM")) {
            repository.save(defaultPremium());
        }
    }

    /**
     * Lists subscription limit settings information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @return the matching result collection
     */
    public List<SubscriptionLimitSettingsResponse> list() {
        ensureDefaults();
        return repository.findAll().stream()
                .sorted(Comparator.comparing(SubscriptionLimitSettings::getPlanType))
                .map(this::toResponse)
                .toList();
    }

    /**
     * Retrieves subscription limit settings information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param planType the plan type parameter
     * @return the get settings result
     */
    public SubscriptionLimitSettings getSettings(String planType) {
        ensureDefaults();
        String normalized = normalizePlanType(planType);
        return repository.findById(normalized)
                .orElseThrow(() -> new AppException(ProjectErrorCode.SUBSCRIPTION_LIMIT_SETTINGS_NOT_FOUND));
    }

    /**
     * Updates subscription limit settings data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param planType the plan type parameter
     * @param request the request parameter
     * @return the update result
     */
    @Transactional
    public SubscriptionLimitSettingsResponse update(String planType, SubscriptionLimitSettingsRequest request) {
        SubscriptionLimitSettings settings = getSettings(planType);
        settings.setMaxOwnedProjects(request.getMaxOwnedProjects());
        settings.setMaxMembersPerProject(request.getMaxMembersPerProject());
        settings.setMaxIssuesPerProject(request.getMaxIssuesPerProject());
        settings.setMaxSprintsPerProject(request.getMaxSprintsPerProject());
        settings.setMaxCustomRolesPerProject(request.getMaxCustomRolesPerProject());
        settings.setActivityLogDays(request.getActivityLogDays());
        settings.setCustomWorkflowEnabled(request.getCustomWorkflowEnabled());
        settings.setBurndownEnabled(request.getBurndownEnabled());
        settings.setIssueTypePriorityCustomizationEnabled(request.getIssueTypePriorityCustomizationEnabled());
        return toResponse(repository.save(settings));
    }

    /**
     * Returns to response for subscription limit settings processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param settings the settings parameter
     * @return the to response result
     */
    public SubscriptionLimitSettingsResponse toResponse(SubscriptionLimitSettings settings) {
        return SubscriptionLimitSettingsResponse.builder()
                .planType(settings.getPlanType())
                .maxOwnedProjects(settings.getMaxOwnedProjects())
                .maxMembersPerProject(settings.getMaxMembersPerProject())
                .maxIssuesPerProject(settings.getMaxIssuesPerProject())
                .maxSprintsPerProject(settings.getMaxSprintsPerProject())
                .maxCustomRolesPerProject(settings.getMaxCustomRolesPerProject())
                .activityLogDays(settings.getActivityLogDays())
                .customWorkflowEnabled(settings.getCustomWorkflowEnabled())
                .burndownEnabled(settings.getBurndownEnabled())
                .issueTypePriorityCustomizationEnabled(settings.getIssueTypePriorityCustomizationEnabled())
                .createdAt(settings.getCreatedAt())
                .updatedAt(settings.getUpdatedAt())
                .build();
    }

    /**
     * Normalizes subscription limit settings content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param planType the plan type parameter
     * @return the normalize plan type result
     * @throws AppException if a business rule prevents the requested operation
     */
    private String normalizePlanType(String planType) {
        String normalized = planType == null ? "FREE" : planType.trim().toUpperCase(Locale.ROOT);
        if (!"FREE".equals(normalized) && !"PREMIUM".equals(normalized)) {
            throw new AppException(ProjectErrorCode.INVALID_REQUEST, "planType must be FREE or PREMIUM");
        }
        return normalized;
    }

    /**
     * Returns default free for subscription limit settings processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @return the default free result
     */
    private SubscriptionLimitSettings defaultFree() {
        return SubscriptionLimitSettings.builder()
                .planType("FREE")
                .maxOwnedProjects(2)
                .maxMembersPerProject(5)
                .maxIssuesPerProject(50)
                .maxSprintsPerProject(2)
                .maxCustomRolesPerProject(2)
                .activityLogDays(7)
                .customWorkflowEnabled(false)
                .burndownEnabled(false)
                .issueTypePriorityCustomizationEnabled(false)
                .build();
    }

    /**
     * Returns default premium for subscription limit settings processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @return the default premium result
     */
    private SubscriptionLimitSettings defaultPremium() {
        return SubscriptionLimitSettings.builder()
                .planType("PREMIUM")
                .maxOwnedProjects(10)
                .maxMembersPerProject(20)
                .maxIssuesPerProject(500)
                .maxSprintsPerProject(10)
                .maxCustomRolesPerProject(999)
                .activityLogDays(60)
                .customWorkflowEnabled(true)
                .burndownEnabled(true)
                .issueTypePriorityCustomizationEnabled(true)
                .build();
    }
}
