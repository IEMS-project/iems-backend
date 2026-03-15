package com.iems.projectservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iems.projectservice.client.UserServiceFeignClient;
import com.iems.projectservice.dto.request.AccountIdsDto;
import com.iems.projectservice.dto.request.CreateIssueDto;
import com.iems.projectservice.dto.request.UpdateIssueDto;
import com.iems.projectservice.dto.response.IssueResponseDto;
import com.iems.projectservice.dto.response.UserDetailDto;
import com.iems.projectservice.dto.response.UserInfoDto;
import com.iems.projectservice.entity.*;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IssueService {

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final IssueTypeRepository issueTypeRepository;
    private final IssuePriorityRepository issuePriorityRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final ActivityLogService activityLogService;
    private final UserServiceFeignClient userServiceFeignClient;
    private final ObjectMapper objectMapper;

    // ── User enrichment helpers ──────────────────────────────────────────────

    private Map<UUID, UserDetailDto> fetchUsers(Set<UUID> accountIds) {
        if (accountIds.isEmpty())
            return Collections.emptyMap();
        try {
            ResponseEntity<Map<String, Object>> response = userServiceFeignClient
                    .getUsersByAccountIds(new AccountIdsDto(accountIds));
            if (response.getBody() != null && response.getBody().get("data") != null) {
                List<UserDetailDto> users = objectMapper.convertValue(
                        response.getBody().get("data"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, UserDetailDto.class));
                return users.stream()
                        .filter(u -> u.getId() != null)
                        .collect(Collectors.toMap(UserDetailDto::getId, u -> u));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user details from IAM service: {}", e.getMessage());
        }
        return new HashMap<>();
    }

    private UserInfoDto toUserInfo(UserDetailDto u) {
        if (u == null)
            return null;
        String name = (u.getFirstName() != null ? u.getFirstName().trim() : "")
                + " "
                + (u.getLastName() != null ? u.getLastName().trim() : "");
        return new UserInfoDto(u.getId(), name.trim(), u.getEmail(), u.getImage());
    }

    private IssueResponseDto toDto(Issue issue, Map<UUID, UserDetailDto> userMap) {
        return IssueResponseDto.builder()
                .id(issue.getId())
                .projectId(issue.getProjectId())
                .issueKey(issue.getIssueKey())
                .issueTypeId(issue.getIssueTypeId())
                .parentId(issue.getParentId())
                .title(issue.getTitle())
                .description(issue.getDescription())
                .statusId(issue.getStatusId())
                .priorityId(issue.getPriorityId())
                .assigneeId(issue.getAssigneeId())
                .reporterId(issue.getReporterId())
                .sprintId(issue.getSprintId())
                .storyPoints(issue.getStoryPoints())
                .sortOrder(issue.getSortOrder())
                .dueDate(issue.getDueDate())
                .createdAt(issue.getCreatedAt())
                .updatedAt(issue.getUpdatedAt())
                .assignee(issue.getAssigneeId() != null ? toUserInfo(userMap.get(issue.getAssigneeId())) : null)
                .reporter(issue.getReporterId() != null ? toUserInfo(userMap.get(issue.getReporterId())) : null)
                .build();
    }

    private IssueResponseDto enrich(Issue issue) {
        Set<UUID> ids = new HashSet<>();
        if (issue.getAssigneeId() != null)
            ids.add(issue.getAssigneeId());
        if (issue.getReporterId() != null)
            ids.add(issue.getReporterId());
        return toDto(issue, fetchUsers(ids));
    }

    private List<IssueResponseDto> enrichList(List<Issue> issues) {
        Set<UUID> ids = new HashSet<>();
        for (Issue i : issues) {
            if (i.getAssigneeId() != null)
                ids.add(i.getAssigneeId());
            if (i.getReporterId() != null)
                ids.add(i.getReporterId());
        }
        Map<UUID, UserDetailDto> userMap = fetchUsers(ids);
        return issues.stream().map(i -> toDto(i, userMap)).collect(Collectors.toList());
    }

    // ── Issue CRUD ───────────────────────────────────────────────────────────

    @Transactional
    public IssueResponseDto createIssue(UUID projectId, CreateIssueDto dto, UUID reporterId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.PROJECT_NOT_FOUND));

        long count = issueRepository.countByProjectId(projectId);
        String issueKey = project.getProjectKey() + "-" + (count + 1);

        Workflow defaultWorkflow = workflowRepository.findByProjectIdAndIsDefaultTrue(projectId)
                .orElse(null);
        UUID defaultStatusId = null;
        if (defaultWorkflow != null) {
            List<WorkflowStatus> statuses = workflowStatusRepository
                    .findByWorkflowIdOrderBySortOrderAsc(defaultWorkflow.getId());
            if (!statuses.isEmpty()) {
                defaultStatusId = statuses.get(0).getId();
            }
        }

        int nextSortOrder = issueRepository.findMaxSortOrderByProjectId(projectId).orElse(0) + 1;

        Issue issue = new Issue();
        issue.setProjectId(projectId);
        issue.setIssueKey(issueKey);
        issue.setIssueTypeId(dto.getIssueTypeId());
        issue.setParentId(dto.getParentId());
        issue.setTitle(dto.getTitle());
        issue.setDescription(dto.getDescription());
        issue.setStatusId(defaultStatusId);
        issue.setPriorityId(dto.getPriorityId());
        issue.setAssigneeId(dto.getAssigneeId());
        issue.setReporterId(reporterId);
        issue.setSprintId(dto.getSprintId());
        issue.setStoryPoints(dto.getStoryPoints());
        issue.setSortOrder(nextSortOrder);

        Issue saved = issueRepository.save(issue);

        activityLogService.log(projectId, saved.getId(), reporterId, "ISSUE_CREATED",
                "Created issue " + issueKey + ": " + dto.getTitle());

        return enrich(saved);
    }

    @Transactional
    public IssueResponseDto updateIssue(UUID issueId, UpdateIssueDto dto, UUID userId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.ISSUE_NOT_FOUND));

        if (dto.getTitle() != null)
            issue.setTitle(dto.getTitle());
        if (dto.getDescription() != null)
            issue.setDescription(dto.getDescription());
        if (dto.getIssueTypeId() != null)
            issue.setIssueTypeId(dto.getIssueTypeId());
        if (dto.getPriorityId() != null)
            issue.setPriorityId(dto.getPriorityId());
        if (dto.getAssigneeId() != null) {
            issue.setAssigneeId(dto.getAssigneeId());
            activityLogService.log(issue.getProjectId(), issueId, userId, "ISSUE_ASSIGNED",
                    "Assigned issue " + issue.getIssueKey());
        }
        if (dto.getSprintId() != null)
            issue.setSprintId(dto.getSprintId());
        if (dto.getParentId() != null)
            issue.setParentId(dto.getParentId());
        if (dto.getStoryPoints() != null)
            issue.setStoryPoints(dto.getStoryPoints());
        if (dto.getSortOrder() != null)
            issue.setSortOrder(dto.getSortOrder());
        if (dto.getDueDate() != null)
            issue.setDueDate(dto.getDueDate());

        if (dto.getStatusId() != null && !dto.getStatusId().equals(issue.getStatusId())) {
            changeStatus(issue, dto.getStatusId(), userId);
        }

        return enrich(issueRepository.save(issue));
    }

    private void changeStatus(Issue issue, UUID newStatusId, UUID userId) {
        String fromName = issue.getStatusId() != null
                ? workflowStatusRepository.findById(issue.getStatusId())
                        .map(WorkflowStatus::getName).orElse("Unknown")
                : "None";
        String toName = workflowStatusRepository.findById(newStatusId)
                .map(WorkflowStatus::getName).orElse("Unknown");

        issue.setStatusId(newStatusId);
        activityLogService.log(
                issue.getProjectId(),
                issue.getId(),
                userId,
                "ISSUE_STATUS_CHANGED",
                issue.getIssueKey() + ": " + fromName + " → " + toName);
    }

    @Transactional
    public void deleteIssue(UUID issueId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.ISSUE_NOT_FOUND));
        issueRepository.delete(issue);
    }

    public IssueResponseDto getIssueById(UUID issueId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.ISSUE_NOT_FOUND));
        return enrich(issue);
    }

    public List<IssueResponseDto> getIssuesByProject(UUID projectId) {
        return enrichList(issueRepository.findByProjectIdOrderBySortOrderAsc(projectId));
    }

    public List<IssueResponseDto> getBacklog(UUID projectId) {
        return enrichList(issueRepository.findByProjectIdAndSprintIdIsNullOrderBySortOrderAsc(projectId));
    }

    public List<IssueResponseDto> getIssuesBySprint(UUID sprintId) {
        return enrichList(issueRepository.findBySprintIdOrderBySortOrderAsc(sprintId));
    }

    public List<IssueResponseDto> getChildIssues(UUID parentId) {
        return enrichList(issueRepository.findByParentId(parentId));
    }

    public List<IssueResponseDto> getMyIssues(UUID assigneeId) {
        return enrichList(issueRepository.findByAssigneeId(assigneeId));
    }

    @Transactional
    public IssueResponseDto addToSprint(UUID issueId, UUID sprintId, UUID userId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.ISSUE_NOT_FOUND));
        issue.setSprintId(sprintId);
        activityLogService.log(issue.getProjectId(), issueId, userId, "ISSUE_MOVED_TO_SPRINT",
                "Moved issue " + issue.getIssueKey() + " to sprint");
        return enrich(issueRepository.save(issue));
    }

    @Transactional
    public IssueResponseDto removeFromSprint(UUID issueId, UUID userId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.ISSUE_NOT_FOUND));
        issue.setSprintId(null);
        activityLogService.log(issue.getProjectId(), issueId, userId, "ISSUE_REMOVED_FROM_SPRINT",
                "Removed issue " + issue.getIssueKey() + " from sprint");
        return enrich(issueRepository.save(issue));
    }

    // ── IssueType CRUD ───────────────────────────────────────────────────────

    public List<IssueType> getIssueTypes(UUID projectId) {
        return issueTypeRepository.findByProjectIdOrderBySortOrderAsc(projectId);
    }

    public IssueType createIssueType(UUID projectId, String name, String description, String iconUrl) {
        IssueType it = new IssueType();
        it.setProjectId(projectId);
        it.setName(name);
        it.setDescription(description);
        it.setIconUrl(iconUrl);
        List<IssueType> existing = issueTypeRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        it.setSortOrder(existing.size());
        return issueTypeRepository.save(it);
    }

    public IssueType updateIssueType(UUID id, String name, String description, String iconUrl) {
        IssueType it = issueTypeRepository.findById(id)
                .orElseThrow(() -> new AppException(ProjectErrorCode.ISSUE_TYPE_NOT_FOUND));
        if (name != null)
            it.setName(name);
        if (description != null)
            it.setDescription(description);
        if (iconUrl != null)
            it.setIconUrl(iconUrl);
        return issueTypeRepository.save(it);
    }

    public void deleteIssueType(UUID id) {
        IssueType it = issueTypeRepository.findById(id)
                .orElseThrow(() -> new AppException(ProjectErrorCode.ISSUE_TYPE_NOT_FOUND));
        issueTypeRepository.delete(it);
    }

    // ── IssuePriority CRUD ───────────────────────────────────────────────────

    public List<IssuePriority> getIssuePriorities(UUID projectId) {
        return issuePriorityRepository.findByProjectIdOrderBySortOrderAsc(projectId);
    }

    public IssuePriority createIssuePriority(UUID projectId, String name, String iconUrl, String color) {
        IssuePriority ip = new IssuePriority();
        ip.setProjectId(projectId);
        ip.setName(name);
        ip.setIconUrl(iconUrl);
        ip.setColor(color);
        List<IssuePriority> existing = issuePriorityRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        ip.setSortOrder(existing.size());
        return issuePriorityRepository.save(ip);
    }

    public IssuePriority updateIssuePriority(UUID id, String name, String iconUrl, String color) {
        IssuePriority ip = issuePriorityRepository.findById(id)
                .orElseThrow(() -> new AppException(ProjectErrorCode.ISSUE_PRIORITY_NOT_FOUND));
        if (name != null)
            ip.setName(name);
        if (iconUrl != null)
            ip.setIconUrl(iconUrl);
        if (color != null)
            ip.setColor(color);
        return issuePriorityRepository.save(ip);
    }

    public void deleteIssuePriority(UUID id) {
        IssuePriority ip = issuePriorityRepository.findById(id)
                .orElseThrow(() -> new AppException(ProjectErrorCode.ISSUE_PRIORITY_NOT_FOUND));
        issuePriorityRepository.delete(ip);
    }

    @Transactional
    public void createDefaultIssueTypes(UUID projectId) {
        createIssueType(projectId, "EPIC", "A large body of work", null);
        createIssueType(projectId, "STORY", "A user story", null);
        createIssueType(projectId, "TASK", "A task to be done", null);
        createIssueType(projectId, "BUG", "A bug to be fixed", null);
        createIssueType(projectId, "SUBTASK", "A subtask", null);
    }

    @Transactional
    public void createDefaultPriorities(UUID projectId) {
        createIssuePriority(projectId, "Highest", null, "#FF0000");
        createIssuePriority(projectId, "High", null, "#FF6B00");
        createIssuePriority(projectId, "Medium", null, "#FFAB00");
        createIssuePriority(projectId, "Low", null, "#2684FF");
        createIssuePriority(projectId, "Lowest", null, "#0065FF");
    }
}
