package com.iems.projectservice.service;

import com.iems.projectservice.dto.request.CreateSprintDto;
import com.iems.projectservice.dto.request.UpdateSprintDto;
import com.iems.projectservice.entity.Issue;
import com.iems.projectservice.entity.Sprint;
import com.iems.projectservice.entity.enums.SprintStatus;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.IssueRepository;
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

    public Sprint createSprint(UUID projectId, CreateSprintDto dto, UUID userId) {
        List<Sprint> existing = sprintRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        
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

    public Sprint updateSprint(UUID sprintId, UpdateSprintDto dto) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.SPRINT_NOT_FOUND));
        if (dto.getName() != null) sprint.setName(dto.getName());
        if (dto.getGoal() != null) sprint.setGoal(dto.getGoal());
        if (dto.getStartDate() != null) sprint.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null) sprint.setEndDate(dto.getEndDate());
        return sprintRepository.save(sprint);
    }

    public void deleteSprint(UUID sprintId) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.SPRINT_NOT_FOUND));
        // Move all issues back to backlog before deleting
        List<Issue> sprintIssues = issueRepository.findBySprintIdOrderBySortOrderAsc(sprintId);
        for (Issue issue : sprintIssues) {
            issue.setSprintId(null);
            issueRepository.save(issue);
        }
        sprintRepository.delete(sprint);
    }

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
        return saved;
    }

    @Transactional
    public Sprint completeSprint(UUID sprintId, UUID userId) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.SPRINT_NOT_FOUND));

        if (sprint.getStatus() != SprintStatus.ACTIVE) {
            throw new AppException(ProjectErrorCode.SPRINT_NOT_ACTIVE);
        }

        // Move incomplete issues back to backlog
        List<Issue> incompleteIssues = issueRepository.findIncompleteIssuesInSprint(sprintId, sprint.getProjectId());
        for (Issue issue : incompleteIssues) {
            issue.setSprintId(null);
            issueRepository.save(issue);
        }

        sprint.setStatus(SprintStatus.COMPLETED);
        sprint.setEndDate(LocalDateTime.now());

        Sprint saved = sprintRepository.save(sprint);
        activityLogService.log(sprint.getProjectId(), null, userId, "SPRINT_COMPLETED",
                "Completed sprint: " + sprint.getName() + ". " + incompleteIssues.size() + " issues moved to backlog.");
        return saved;
    }

    @Transactional
    public Sprint cancelSprint(UUID sprintId, UUID userId) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.SPRINT_NOT_FOUND));

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

    public Sprint getSprintById(UUID sprintId) {
        return sprintRepository.findById(sprintId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.SPRINT_NOT_FOUND));
    }

    public List<Sprint> getSprintsByProject(UUID projectId) {
        return sprintRepository.findByProjectIdOrderBySortOrderAsc(projectId);
    }
}
