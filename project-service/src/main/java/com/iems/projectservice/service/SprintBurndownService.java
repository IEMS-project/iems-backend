package com.iems.projectservice.service;

import com.iems.projectservice.dto.response.BurndownDataPointDto;
import com.iems.projectservice.dto.response.SprintBurndownDto;
import com.iems.projectservice.entity.Issue;
import com.iems.projectservice.entity.IssueStatusHistory;
import com.iems.projectservice.entity.Sprint;
import com.iems.projectservice.entity.Workflow;
import com.iems.projectservice.entity.WorkflowStatus;
import com.iems.projectservice.entity.enums.StatusCategory;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.IssueRepository;
import com.iems.projectservice.repository.IssueStatusHistoryRepository;
import com.iems.projectservice.repository.SprintRepository;
import com.iems.projectservice.repository.WorkflowRepository;
import com.iems.projectservice.repository.WorkflowStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SprintBurndownService {

    private final SprintRepository sprintRepository;
    private final IssueRepository issueRepository;
    private final IssueStatusHistoryRepository issueStatusHistoryRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowStatusRepository workflowStatusRepository;

    /**
     * Retrieves sprint burndown information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param sprintId the sprint id parameter
     * @return the get sprint burndown result
     */
    public SprintBurndownDto getSprintBurndown(UUID sprintId) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.SPRINT_NOT_FOUND));

        LocalDate startDate = resolveStartDate(sprint);
        LocalDate endDate = resolveEndDate(sprint, startDate);

        List<Issue> sprintIssues = issueRepository.findBySprintIdOrderBySortOrderAsc(sprintId);
        Map<UUID, Issue> issueMap = sprintIssues.stream().collect(Collectors.toMap(Issue::getId, i -> i));

        List<IssueStatusHistory> history = issueStatusHistoryRepository.findBySprintIdOrderByChangedAtAsc(sprintId);

        Set<UUID> allIssueIds = new HashSet<>(issueMap.keySet());
        history.stream().map(IssueStatusHistory::getIssueId).filter(Objects::nonNull).forEach(allIssueIds::add);

        if (!allIssueIds.isEmpty()) {
            List<IssueStatusHistory> allIssueHistories = issueStatusHistoryRepository
                    .findByIssueIdInOrderByChangedAtAsc(allIssueIds);
            for (IssueStatusHistory event : allIssueHistories) {
                issueMap.computeIfAbsent(event.getIssueId(), key -> {
                    Issue issue = new Issue();
                    issue.setId(event.getIssueId());
                    issue.setProjectId(event.getProjectId());
                    issue.setStoryPoints(event.getStoryPoints());
                    issue.setSprintId(event.getSprintId());
                    return issue;
                });
                if (issueMap.get(event.getIssueId()).getStoryPoints() == null && event.getStoryPoints() != null) {
                    issueMap.get(event.getIssueId()).setStoryPoints(event.getStoryPoints());
                }
            }
        }

        List<Workflow> projectWorkflows = workflowRepository.findByProjectId(sprint.getProjectId());
        List<UUID> workflowIds = projectWorkflows.stream().map(Workflow::getId).toList();
        Set<UUID> doneStatusIds = workflowIds.isEmpty()
                ? Collections.emptySet()
                : workflowStatusRepository
                        .findByWorkflowIdInAndCategory(workflowIds, StatusCategory.DONE)
                        .stream()
                        .map(WorkflowStatus::getId)
                        .collect(Collectors.toSet());

        int totalStoryPoints = issueMap.values().stream()
                .map(Issue::getStoryPoints)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum);

        Map<LocalDate, Integer> completedDeltaByDate = new HashMap<>();
        for (IssueStatusHistory event : history) {
            if (event.getIssueId() == null || event.getChangedAt() == null) {
                continue;
            }
            Issue issue = issueMap.get(event.getIssueId());
            int points = Optional.ofNullable(event.getStoryPoints())
                    .or(() -> Optional.ofNullable(issue).map(Issue::getStoryPoints))
                    .orElse(0);
            if (points == 0) {
                continue;
            }

            boolean movedToDone = doneStatusIds.contains(event.getToStatusId())
                    && !doneStatusIds.contains(event.getFromStatusId());
            boolean movedOutOfDone = doneStatusIds.contains(event.getFromStatusId())
                    && !doneStatusIds.contains(event.getToStatusId());

            if (!movedToDone && !movedOutOfDone) {
                continue;
            }

            LocalDate changedDate = event.getChangedAt().toLocalDate();
            if (changedDate.isBefore(startDate) || changedDate.isAfter(endDate)) {
                continue;
            }

            int delta = movedToDone ? points : -points;
            completedDeltaByDate.merge(changedDate, delta, Integer::sum);
        }

        List<BurndownDataPointDto> points = new ArrayList<>();
        int dayCount = (int) ChronoUnit.DAYS.between(startDate, endDate);
        int cumulativeCompleted = 0;

        for (int dayOffset = 0; dayOffset <= dayCount; dayOffset++) {
            LocalDate date = startDate.plusDays(dayOffset);
            cumulativeCompleted += completedDeltaByDate.getOrDefault(date, 0);
            int actualRemaining = Math.max(0, totalStoryPoints - cumulativeCompleted);

            int idealRemaining;
            if (dayCount == 0) {
                idealRemaining = 0;
            } else {
                double ratio = (double) dayOffset / dayCount;
                idealRemaining = Math.max(0, (int) Math.round(totalStoryPoints * (1 - ratio)));
            }

            points.add(new BurndownDataPointDto(date, idealRemaining, actualRemaining));
        }

        int currentRemaining = points.isEmpty() ? totalStoryPoints : points.get(points.size() - 1).getActualRemaining();

        return new SprintBurndownDto(
                sprint.getId(),
                sprint.getName(),
                startDate,
                endDate,
                totalStoryPoints,
                currentRemaining,
                points);
    }

    /**
     * Resolves sprint burndown information for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param sprint the sprint parameter
     * @return the resolve start date result
     */
    private LocalDate resolveStartDate(Sprint sprint) {
        if (sprint.getStartDate() != null) {
            return sprint.getStartDate().toLocalDate();
        }
        if (sprint.getCreatedAt() != null) {
            return sprint.getCreatedAt().toLocalDate();
        }
        return LocalDate.now();
    }

    /**
     * Resolves sprint burndown information for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param sprint the sprint parameter
     * @param startDate the start date parameter
     * @return the resolve end date result
     */
    private LocalDate resolveEndDate(Sprint sprint, LocalDate startDate) {
        if (sprint.getEndDate() != null) {
            LocalDate end = sprint.getEndDate().toLocalDate();
            return end.isBefore(startDate) ? startDate : end;
        }
        LocalDate fallback = LocalDate.now();
        return fallback.isBefore(startDate) ? startDate : fallback;
    }
}
