package com.iems.projectservice.service;

import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.entity.SubscriptionLimitSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Central service that enforces subscription limits.
 *
 * Two levels:
 *  - Account-level  : based on the CURRENT user's subscription (e.g. how many projects they can create)
 *  - Project-level  : based on the PROJECT OWNER's subscription (e.g. how many members/issues a project can have)
 *
 * Subscription type is read from the JWT claim "subscriptionType" so we never need a round-trip to IAM.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SubscriptionLimitService {

    private final SubscriptionLimitSettingsService settingsService;

    // ── Limits ────────────────────────────────────────────────────────────────

    public static final int FREE_MAX_OWNED_PROJECTS = 2;
    public static final int PREMIUM_MAX_OWNED_PROJECTS = 10;

    public static final int FREE_MAX_MEMBERS_PER_PROJECT = 5;
    public static final int PREMIUM_MAX_MEMBERS_PER_PROJECT = 20;

    public static final int FREE_MAX_ISSUES_PER_PROJECT = 50;
    public static final int PREMIUM_MAX_ISSUES_PER_PROJECT = 500;

    public static final int FREE_MAX_SPRINTS_PER_PROJECT = 2;
    public static final int PREMIUM_MAX_SPRINTS_PER_PROJECT = 10;

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Extract "subscriptionType" claim from the current SecurityContext JWT.
     * Returns "FREE" if the claim is absent (safe default).
     */
    public String getCurrentUserSubscription() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getCredentials() instanceof String token) {
                // Token is stored as credentials in our JwtAuthenticationFilter
                return extractSubscriptionFromToken(token);
            }
            // Fall-back: read from authorities (e.g. SUBSCRIPTION_PREMIUM granted authority)
            if (auth != null) {
                return auth.getAuthorities().stream()
                        .map(a -> a.getAuthority())
                        .filter(a -> a.startsWith("SUBSCRIPTION_"))
                        .map(a -> a.replace("SUBSCRIPTION_", ""))
                        .findFirst()
                        .orElse("FREE");
            }
        } catch (Exception e) {
            log.debug("Could not extract subscription from SecurityContext: {}", e.getMessage());
        }
        return "FREE";
    }

    private String extractSubscriptionFromToken(String token) {
        try {
            // JWT is base64url: header.payload.signature
            String[] parts = token.split("\\.");
            if (parts.length < 2) return "FREE";
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            // Simple JSON key scan (no external dependency needed)
            if (payload.contains("\"subscriptionType\":\"PREMIUM\"")) return "PREMIUM";
        } catch (Exception e) {
            log.debug("JWT payload decode failed: {}", e.getMessage());
        }
        return "FREE";
    }

    public boolean isCurrentUserPremium() {
        return "PREMIUM".equalsIgnoreCase(getCurrentUserSubscription());
    }

    /**
     * Determine the effective subscription of a project based on the owner's stored subscription
     * that was synced when the project was saved. This avoids a Feign call per request.
     *
     * @param ownerSubscription the ownerSubscription field from the Project entity
     */
    public boolean isProjectPremium(String ownerSubscription) {
        return "PREMIUM".equalsIgnoreCase(ownerSubscription);
    }

    // ── Guard methods (throw AppException if limit exceeded) ──────────────────

    /**
     * Account-level: check if current user can create another project.
     * @param currentOwnedCount number of projects the user currently owns
     */
    public void checkCanCreateProject(long currentOwnedCount) {
        boolean premium = isCurrentUserPremium();
        int limit = settingsFor(premium).getMaxOwnedProjects();
        if (currentOwnedCount >= limit) {
            log.warn("Project creation blocked: owned={}, limit={}, premium={}", currentOwnedCount, limit, premium);
            throw new AppException(ProjectErrorCode.PROJECT_LIMIT_EXCEEDED,
                    "You have reached the maximum number of projects (" + limit + ") for your plan. "
                    + (premium ? "" : "Upgrade to Premium to create more."));
        }
    }

    /**
     * Project-level: check if a new member can be added to the project.
     * @param currentMemberCount current number of members in the project
     * @param ownerSubscription  subscription of the project owner
     */
    public void checkCanAddMember(long currentMemberCount, String ownerSubscription) {
        boolean premium = isProjectPremium(ownerSubscription);
        int limit = settingsFor(premium).getMaxMembersPerProject();
        if (currentMemberCount >= limit) {
            log.warn("Member add blocked: count={}, limit={}, ownerSub={}", currentMemberCount, limit, ownerSubscription);
            throw new AppException(ProjectErrorCode.MEMBER_LIMIT_EXCEEDED,
                    "This project has reached the maximum number of members (" + limit + "). "
                    + (premium ? "" : "The project owner needs to upgrade to Premium to add more."));
        }
    }

    /**
     * Project-level: check if a new issue can be created in the project.
     * @param currentIssueCount current number of issues in the project
     * @param ownerSubscription subscription of the project owner
     */
    public void checkCanCreateIssue(long currentIssueCount, String ownerSubscription) {
        boolean premium = isProjectPremium(ownerSubscription);
        int limit = settingsFor(premium).getMaxIssuesPerProject();
        if (currentIssueCount >= limit) {
            log.warn("Issue creation blocked: count={}, limit={}, ownerSub={}", currentIssueCount, limit, ownerSubscription);
            throw new AppException(ProjectErrorCode.ISSUE_LIMIT_EXCEEDED,
                    "This project has reached the maximum number of issues (" + limit + "). "
                    + (premium ? "" : "The project owner needs to upgrade to Premium to create more."));
        }
    }

    /**
     * Project-level: check if a new sprint can be created.
     * @param currentSprintCount current total number of sprints (not just active) in the project
     * @param ownerSubscription  subscription of the project owner
     */
    public void checkCanCreateSprint(long currentSprintCount, String ownerSubscription) {
        boolean premium = isProjectPremium(ownerSubscription);
        int limit = settingsFor(premium).getMaxSprintsPerProject();
        if (currentSprintCount >= limit) {
            log.warn("Sprint creation blocked: count={}, limit={}, ownerSub={}", currentSprintCount, limit, ownerSubscription);
            throw new AppException(ProjectErrorCode.SPRINT_LIMIT_EXCEEDED,
                    "This project has reached the maximum number of sprints (" + limit + "). "
                    + (premium ? "" : "The project owner needs to upgrade to Premium to create more."));
        }
    }

    /**
     * Project-level: check if a custom workflow can be created.
     * Only PREMIUM project owners may create custom workflows.
     * @param ownerSubscription subscription of the project owner
     */
    public void checkCanCreateCustomWorkflow(String ownerSubscription) {
        SubscriptionLimitSettings settings = settingsFor(isProjectPremium(ownerSubscription));
        if (!Boolean.TRUE.equals(settings.getCustomWorkflowEnabled())) {
            throw new AppException(ProjectErrorCode.PREMIUM_REQUIRED,
                    "Custom workflows are available for Premium projects only. The project owner needs to upgrade to Premium.");
        }
    }

    /**
     * Project-level: check if a custom role can be created.
     * Free projects may have at most FREE_MAX_CUSTOM_ROLES custom (non-default) roles.
     * @param currentCustomRoleCount number of existing non-default roles in the project
     * @param ownerSubscription      subscription of the project owner
     */
    public static final int FREE_MAX_CUSTOM_ROLES = 2;

    public void checkCanCreateCustomRole(long currentCustomRoleCount, String ownerSubscription) {
        SubscriptionLimitSettings settings = settingsFor(isProjectPremium(ownerSubscription));
        int limit = settings.getMaxCustomRolesPerProject();
        if (currentCustomRoleCount >= limit) {
            throw new AppException(ProjectErrorCode.PREMIUM_REQUIRED,
                    "This project may only have " + limit + " custom roles. "
                    + "Upgrade to Premium to create more.");
        }
    }

    /**
     * Project-level: check if the burndown chart can be viewed.
     * Only PREMIUM project owners may access the burndown chart.
     * @param ownerSubscription subscription of the project owner
     */
    public void checkBurndownAccess(String ownerSubscription) {
        SubscriptionLimitSettings settings = settingsFor(isProjectPremium(ownerSubscription));
        if (!Boolean.TRUE.equals(settings.getBurndownEnabled())) {
            throw new AppException(ProjectErrorCode.PREMIUM_REQUIRED,
                    "Burndown chart is available for Premium projects only. The project owner needs to upgrade to Premium.");
        }
    }

    /**
     * Project-level: block any write operation (create/update/delete) on workflows for free projects.
     * Free projects can only READ the default workflow.
     */
    public void checkCanModifyWorkflow(String ownerSubscription) {
        SubscriptionLimitSettings settings = settingsFor(isProjectPremium(ownerSubscription));
        if (!Boolean.TRUE.equals(settings.getCustomWorkflowEnabled())) {
            throw new AppException(ProjectErrorCode.PREMIUM_REQUIRED,
                    "Modifying workflows (statuses, transitions) is available for Premium projects only. "
                    + "The project owner needs to upgrade to Premium.");
        }
    }

    /**
     * Project-level: block any write operation on issue priorities for free projects.
     */
    public void checkCanModifyIssuePriority(String ownerSubscription) {
        SubscriptionLimitSettings settings = settingsFor(isProjectPremium(ownerSubscription));
        if (!Boolean.TRUE.equals(settings.getIssueTypePriorityCustomizationEnabled())) {
            throw new AppException(ProjectErrorCode.PREMIUM_REQUIRED,
                    "Modifying issue priorities is available for Premium projects only. "
                    + "The project owner needs to upgrade to Premium.");
        }
    }

    /**
     * Project-level: block any write operation on issue types for free projects.
     */
    public void checkCanModifyIssueType(String ownerSubscription) {
        SubscriptionLimitSettings settings = settingsFor(isProjectPremium(ownerSubscription));
        if (!Boolean.TRUE.equals(settings.getIssueTypePriorityCustomizationEnabled())) {
            throw new AppException(ProjectErrorCode.PREMIUM_REQUIRED,
                    "Modifying issue types is available for Premium projects only. "
                    + "The project owner needs to upgrade to Premium.");
        }
    }

    /**
     * Returns the number of days of activity log history allowed for the project.
     */
    public int getActivityLogDaysLimit(String ownerSubscription) {
        return settingsFor(isProjectPremium(ownerSubscription)).getActivityLogDays();
    }

    public SubscriptionLimitSettings settingsFor(boolean premium) {
        return settingsService.getSettings(premium ? "PREMIUM" : "FREE");
    }
}
