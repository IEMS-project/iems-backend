package com.iems.projectservice.service;

import com.iems.projectservice.dto.request.CreateSprintDto;
import com.iems.projectservice.dto.request.UpdateSprintDto;
import com.iems.projectservice.entity.Issue;
import com.iems.projectservice.entity.ProjectMember;
import com.iems.projectservice.entity.Sprint;
import com.iems.projectservice.entity.enums.SprintStatus;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.IssueRepository;
import com.iems.projectservice.repository.ProjectMemberRepository;
import com.iems.projectservice.repository.ProjectRepository;
import com.iems.projectservice.repository.SprintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SprintService {

    private final SprintRepository sprintRepository;
    private final IssueRepository issueRepository;
    private final ActivityLogService activityLogService;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final SubscriptionLimitService subscriptionLimitService;
    private final NotificationPublisher notificationPublisher;
    private final ActorNameResolver actorNameResolver;

    /**
     * Creates sprint data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param dto the dto parameter
     * @param userId the user id parameter
     * @return the create sprint result
     */
    public Sprint createSprint(UUID projectId, CreateSprintDto dto, UUID userId) {
        List<Sprint> existing = sprintRepository.findByProjectIdOrderBySortOrderAsc(projectId);

        // Check sprint limit based on project owner's subscription
        String ownerSub = projectRepository.findById(projectId)
                .map(p -> p.getOwnerSubscription()).orElse("FREE");
        subscriptionLimitService.checkCanCreateSprint(existing.size(), ownerSub);
        
        Sprint sprint = new Sprint();
        sprint.setProjectId(projectId);
        sprint.setName(dto.getName());
        sprint.setGoal(dto.getGoal());
        sprint.setStartDate(dto.getStartDate());
        sprint.setEndDate(dto.getEndDate());
        sprint.setStatus(SprintStatus.PLANNED);
        sprint.setSortOrder(existing.size());

        Sprint saved = sprintRepository.save(sprint);
        activityLogService.log(projectId, null, userId, "SPRINT_CREATED", "Created sprint: " + dto.getName());
        return saved;
    }

    /**
     * Updates sprint data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param sprintId the sprint id parameter
     * @param dto the dto parameter
     * @return the update sprint result
     */
    public Sprint updateSprint(UUID sprintId, UpdateSprintDto dto) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.SPRINT_NOT_FOUND));
        ensureSprintNotCompleted(sprint);
        if (dto.getName() != null) sprint.setName(dto.getName());
        if (dto.getGoal() != null) sprint.setGoal(dto.getGoal());
        if (dto.getStartDate() != null) sprint.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null) sprint.setEndDate(dto.getEndDate());
        return sprintRepository.save(sprint);
    }

    /**
     * Deletes sprint data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param sprintId the sprint id parameter
     */
    public void deleteSprint(UUID sprintId) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.SPRINT_NOT_FOUND));
        ensureSprintNotCompleted(sprint);
        // Move all issues back to backlog before deleting
        List<Issue> sprintIssues = issueRepository.findBySprintIdOrderBySortOrderAsc(sprintId);
        for (Issue issue : sprintIssues) {
            issue.setSprintId(null);
            issueRepository.save(issue);
        }
        sprintRepository.delete(sprint);
    }

    /**
     * Returns start sprint for sprint processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Persist the resulting domain changes.</li>
     *   <li>Send the required notification or outbound message.</li>
     * </ul>
     *
     * @param sprintId the sprint id parameter
     * @param userId the user id parameter
     * @return the start sprint result
     * @throws AppException if a business rule prevents the requested operation
     */
    @Transactional
    public Sprint startSprint(UUID sprintId, UUID userId) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.SPRINT_NOT_FOUND));

        if (sprint.getStatus() != SprintStatus.PLANNED) {
            throw new AppException(ProjectErrorCode.INVALID_REQUEST);
        }

        // Check no other active sprint in this project
        if (sprintRepository.existsByProjectIdAndStatus(sprint.getProjectId(), SprintStatus.ACTIVE)) {
            throw new AppException(ProjectErrorCode.SPRINT_ALREADY_ACTIVE);
        }

        sprint.setStatus(SprintStatus.ACTIVE);
        if (sprint.getStartDate() == null) {
            sprint.setStartDate(LocalDateTime.now());
        }

        Sprint saved = sprintRepository.save(sprint);
        activityLogService.log(sprint.getProjectId(), null, userId, "SPRINT_STARTED",
                "Started sprint: " + sprint.getName());

        // Notify all project members
        try {
            var project = projectRepository.findById(sprint.getProjectId()).orElse(null);
            String projectName = project != null ? project.getName() : "";
            String actorName = actorNameResolver.resolve(userId);
            List<UUID> memberIds = projectMemberRepository.findByProjectId(sprint.getProjectId())
                    .stream().map(ProjectMember::getAccountId).toList();
            notificationPublisher.notifySprintStarted(memberIds, userId, actorName,
                    saved.getId(), saved.getName(), saved.getProjectId(), projectName);
        } catch (Exception e) {
            log.warn("Failed to send sprint started notifications: {}", e.getMessage());
        }

        return saved;
    }

    /**
     * Returns complete sprint for sprint processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Persist the resulting domain changes.</li>
     *   <li>Send the required notification or outbound message.</li>
     * </ul>
     *
     * @param sprintId the sprint id parameter
     * @param userId the user id parameter
     * @return the complete sprint result
     * @throws AppException if a business rule prevents the requested operation
     */
    @Transactional
    public Sprint completeSprint(UUID sprintId, UUID userId) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.SPRINT_NOT_FOUND));

        if (sprint.getStatus() != SprintStatus.ACTIVE) {
            throw new AppException(ProjectErrorCode.SPRINT_NOT_ACTIVE);
        }

        List<Issue> incompleteIssues = issueRepository.findIncompleteIssuesInSprint(sprintId, sprint.getProjectId());
        for (Issue issue : incompleteIssues) {
            issue.setSprintId(null);
            issueRepository.save(issue);
        }

        sprint.setStatus(SprintStatus.COMPLETED);
        sprint.setEndDate(LocalDateTime.now());

        Sprint saved = sprintRepository.save(sprint);
        activityLogService.log(sprint.getProjectId(), null, userId, "SPRINT_COMPLETED",
                "Completed sprint: " + sprint.getName() + ". " + incompleteIssues.size() + " incomplete issues moved to backlog.");

        // Notify all project members
        try {
            var project = projectRepository.findById(sprint.getProjectId()).orElse(null);
            String projectName = project != null ? project.getName() : "";
            String actorName = actorNameResolver.resolve(userId);
            List<UUID> memberIds = projectMemberRepository.findByProjectId(sprint.getProjectId())
                    .stream().map(ProjectMember::getAccountId).toList();
            notificationPublisher.notifySprintCompleted(memberIds, userId, actorName,
                    saved.getId(), saved.getName(), saved.getProjectId(), projectName);
        } catch (Exception e) {
            log.warn("Failed to send sprint completed notifications: {}", e.getMessage());
        }

        return saved;
    }

    /**
     * Returns cancel sprint for sprint processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param sprintId the sprint id parameter
     * @param userId the user id parameter
     * @return the cancel sprint result
     */
    @Transactional
    public Sprint cancelSprint(UUID sprintId, UUID userId) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.SPRINT_NOT_FOUND));
        ensureSprintNotCompleted(sprint);

        // Move all issues back to backlog
        List<Issue> sprintIssues = issueRepository.findBySprintIdOrderBySortOrderAsc(sprintId);
        for (Issue issue : sprintIssues) {
            issue.setSprintId(null);
            issueRepository.save(issue);
        }

        sprint.setStatus(SprintStatus.CANCELLED);
        Sprint saved = sprintRepository.save(sprint);
        activityLogService.log(sprint.getProjectId(), null, userId, "SPRINT_CANCELLED",
                "Cancelled sprint: " + sprint.getName());
        return saved;
    }

    /**
     * Retrieves sprint information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param sprintId the sprint id parameter
     * @return the get sprint by id result
     */
    public Sprint getSprintById(UUID sprintId) {
        return sprintRepository.findById(sprintId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.SPRINT_NOT_FOUND));
    }

    /**
     * Retrieves sprint information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @return the matching result collection
     */
    public List<Sprint> getSprintsByProject(UUID projectId) {
        return sprintRepository.findByProjectIdOrderBySortOrderAsc(projectId);
    }

    /**
     * Ensures that sprint requirements are satisfied.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param sprint the sprint parameter
     * @throws AppException if a business rule prevents the requested operation
     */
    private void ensureSprintNotCompleted(Sprint sprint) {
        if (sprint.getStatus() == SprintStatus.COMPLETED) {
            throw new AppException(ProjectErrorCode.SPRINT_ALREADY_COMPLETED);
        }
    }
}
