package com.iems.projectservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iems.projectservice.client.UserServiceFeignClient;
import com.iems.projectservice.dto.response.AccountSubscriptionResponseDto;
import com.iems.projectservice.entity.Project;
import com.iems.projectservice.entity.enums.MemberStatus;
import com.iems.projectservice.repository.IssueRepository;
import com.iems.projectservice.repository.ProjectMemberRepository;
import com.iems.projectservice.repository.ProjectRepository;
import com.iems.projectservice.repository.SprintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectSubscriptionSyncService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final IssueRepository issueRepository;
    private final SprintRepository sprintRepository;
    private final UserServiceFeignClient userServiceFeignClient;
    private final ObjectMapper objectMapper;
    private final SubscriptionLimitService subscriptionLimitService;

    /**
     * Refreshes project subscription sync state.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param project the project parameter
     * @return the refresh project subscription result
     */
    @Transactional
    public Project refreshProjectSubscription(Project project) {
        if (project == null || project.getManagerAccountId() == null) {
            return project;
        }

        try {
            ResponseEntity<Map<String, Object>> response = userServiceFeignClient
                    .getAccountSubscription(project.getManagerAccountId());
            if (response.getBody() == null || response.getBody().get("data") == null) {
                return project;
            }

            AccountSubscriptionResponseDto subscription = objectMapper.convertValue(
                    response.getBody().get("data"),
                    AccountSubscriptionResponseDto.class);

            String effectiveSubscription = resolveEffectiveSubscription(subscription);
            boolean changed = false;

            if (!effectiveSubscription.equalsIgnoreCase(project.getOwnerSubscription())) {
                project.setOwnerSubscription(effectiveSubscription);
                changed = true;
            }

            if ("PREMIUM".equalsIgnoreCase(effectiveSubscription) && project.isLocked()) {
                project.setLocked(false);
                project.setLockReason(null);
                changed = true;
            }

            if ("FREE".equalsIgnoreCase(effectiveSubscription)) {
                boolean violates = isViolatingFreeLimits(project);
                if (violates && !project.isLocked()) {
                    project.setLocked(true);
                    project.setLockReason(
                            "Project locked because the owner's Premium subscription has expired " +
                            "and this project exceeds the FREE plan limits. " +
                            "The owner must upgrade to Premium or reduce usage to unlock.");
                    changed = true;
                } else if (!violates && project.isLocked()) {
                    project.setLocked(false);
                    project.setLockReason(null);
                    changed = true;
                }
            }

            if (changed) {
                return projectRepository.save(project);
            }
        } catch (Exception e) {
            log.warn("Failed to refresh subscription for project {} owner {}: {}",
                    project.getId(), project.getManagerAccountId(), e.getMessage());
        }

        return project;
    }

    /**
     * Resolves project subscription sync information for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param subscription the subscription parameter
     * @return the resolve effective subscription result
     */
    private String resolveEffectiveSubscription(AccountSubscriptionResponseDto subscription) {
        if (subscription == null || !"PREMIUM".equalsIgnoreCase(subscription.getSubscriptionType())) {
            return "FREE";
        }
        Instant premiumUntil = subscription.getPremiumUntil();
        return premiumUntil == null || premiumUntil.isAfter(Instant.now()) ? "PREMIUM" : "FREE";
    }

    /**
     * Returns is violating free limits for project subscription sync processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param project the project parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    private boolean isViolatingFreeLimits(Project project) {
        var freeSettings = subscriptionLimitService.settingsFor(false);

        long ownedProjectCount = projectRepository.countByManagerAccountId(project.getManagerAccountId());
        if (ownedProjectCount > freeSettings.getMaxOwnedProjects()) {
            return true;
        }

        long memberCount = projectMemberRepository.countByProjectIdAndStatus(project.getId(), MemberStatus.ACTIVE);
        if (memberCount > freeSettings.getMaxMembersPerProject()) {
            return true;
        }

        long issueCount = issueRepository.countByProjectId(project.getId());
        if (issueCount > freeSettings.getMaxIssuesPerProject()) {
            return true;
        }

        long sprintCount = sprintRepository.countByProjectId(project.getId());
        return sprintCount > freeSettings.getMaxSprintsPerProject();
    }
}
