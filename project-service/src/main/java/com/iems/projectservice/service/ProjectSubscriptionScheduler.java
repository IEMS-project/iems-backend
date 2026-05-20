package com.iems.projectservice.service;

import com.iems.projectservice.entity.Project;
import com.iems.projectservice.repository.IssueRepository;
import com.iems.projectservice.repository.ProjectMemberRepository;
import com.iems.projectservice.repository.ProjectRepository;
import com.iems.projectservice.repository.SprintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Scheduled service that:
 *  1. (Daily) Locks projects whose owner's premium has expired AND that violate FREE-tier limits.
 *  2. Checks if a locked project now complies with FREE-tier limits and unlocks it automatically.
 *
 * Note: The expiry warning notification (7 days before) is sent by IAM-service, not here.
 * This service only handles the project-side lock/unlock logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectSubscriptionScheduler {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final IssueRepository issueRepository;
    private final SprintRepository sprintRepository;

    /**
     * Run once every day at 02:00 to scan all previously-PREMIUM projects that have been
     * downgraded (ownerSubscription changed to FREE) and lock them if they violate FREE limits.
     *
     * Also check locked projects whose owner may have renewed — unlock them if subscription
     * is now PREMIUM again.
     */
    @Scheduled(cron = "0 0 2 * * *") // daily at 02:00
    @Transactional
    public void evaluateProjectLocks() {
        log.info("[ProjectSubscriptionScheduler] Starting daily project lock evaluation...");

        List<Project> allProjects = projectRepository.findAll();
        int locked = 0;
        int unlocked = 0;

        for (Project project : allProjects) {
            boolean isPremium = "PREMIUM".equalsIgnoreCase(project.getOwnerSubscription());

            if (isPremium) {
                // If project is locked but owner is premium again → unlock
                if (project.isLocked()) {
                    project.setLocked(false);
                    project.setLockReason(null);
                    projectRepository.save(project);
                    unlocked++;
                    log.info("[Scheduler] Unlocked project {} (owner renewed Premium)", project.getId());
                }
                continue;
            }

            // Owner is FREE → check if the project violates FREE limits
            boolean violates = isViolatingFreeLimits(project);

            if (violates && !project.isLocked()) {
                project.setLocked(true);
                project.setLockReason(
                        "Project locked because the owner's Premium subscription has expired " +
                        "and this project exceeds the FREE plan limits. " +
                        "The owner must upgrade to Premium or reduce members/issues to unlock.");
                projectRepository.save(project);
                locked++;
                log.warn("[Scheduler] Locked project {} (FREE limit violation after premium expiry)", project.getId());
            } else if (!violates && project.isLocked()) {
                // Owner is FREE but project no longer violates → auto-unlock
                project.setLocked(false);
                project.setLockReason(null);
                projectRepository.save(project);
                unlocked++;
                log.info("[Scheduler] Auto-unlocked project {} (now within FREE limits)", project.getId());
            }
        }

        log.info("[ProjectSubscriptionScheduler] Done. locked={}, unlocked={}", locked, unlocked);
    }

    /**
     * Check if a project violates FREE-tier limits.
     */
    private boolean isViolatingFreeLimits(Project project) {
        long memberCount = projectMemberRepository.countByProjectId(project.getId());
        if (memberCount > SubscriptionLimitService.FREE_MAX_MEMBERS_PER_PROJECT) return true;

        long issueCount = issueRepository.countByProjectId(project.getId());
        if (issueCount > SubscriptionLimitService.FREE_MAX_ISSUES_PER_PROJECT) return true;

        long sprintCount = sprintRepository.countByProjectId(project.getId());
        if (sprintCount > SubscriptionLimitService.FREE_MAX_SPRINTS_PER_PROJECT) return true;

        return false;
    }
}
