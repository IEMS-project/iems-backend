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

    @Transactional
    public void ensureDefaults() {
        if (!repository.existsById("FREE")) {
            repository.save(defaultFree());
        }
        if (!repository.existsById("PREMIUM")) {
            repository.save(defaultPremium());
        }
    }

    public List<SubscriptionLimitSettingsResponse> list() {
        ensureDefaults();
        return repository.findAll().stream()
                .sorted(Comparator.comparing(SubscriptionLimitSettings::getPlanType))
                .map(this::toResponse)
                .toList();
    }

    public SubscriptionLimitSettings getSettings(String planType) {
        ensureDefaults();
        String normalized = normalizePlanType(planType);
        return repository.findById(normalized)
                .orElseThrow(() -> new AppException(ProjectErrorCode.SUBSCRIPTION_LIMIT_SETTINGS_NOT_FOUND));
    }

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

    private String normalizePlanType(String planType) {
        String normalized = planType == null ? "FREE" : planType.trim().toUpperCase(Locale.ROOT);
        if (!"FREE".equals(normalized) && !"PREMIUM".equals(normalized)) {
            throw new AppException(ProjectErrorCode.INVALID_REQUEST, "planType must be FREE or PREMIUM");
        }
        return normalized;
    }

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
