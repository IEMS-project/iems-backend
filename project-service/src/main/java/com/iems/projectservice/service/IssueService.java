package com.iems.projectservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iems.projectservice.client.UserServiceFeignClient;
import com.iems.projectservice.dto.request.AccountIdsDto;
import com.iems.projectservice.dto.request.CreateIssueDto;
import com.iems.projectservice.dto.request.AttachmentRequestDto;
import com.iems.projectservice.dto.request.IssuePrioritySyncItemDto;
import com.iems.projectservice.dto.request.IssueTypeSyncItemDto;
import com.iems.projectservice.dto.request.UpdateIssueDto;
import com.iems.projectservice.dto.response.IssueImportResultDto;
import com.iems.projectservice.dto.response.PagedResponseDto;
import com.iems.projectservice.dto.response.IssueResponseDto;
import com.iems.projectservice.dto.response.AttachmentResponseDto;
import com.iems.projectservice.dto.response.UserDetailDto;
import com.iems.projectservice.dto.response.UserInfoDto;
import com.iems.projectservice.entity.*;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFDataValidation;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IssueService {

    private static final String[] IMPORT_HEADERS = {
            "Issue Key", "Issue Type", "Title", "Description", "Priority", "Assignee Email", "Sprint Name",
            "Story Points", "Due Date"
    };
    private static final int IMPORT_DATA_START_ROW_INDEX = 1;
    private static final int IMPORT_DATA_END_ROW_INDEX = 500;

    private record ParsedIssueImportRow(String issueKey, CreateIssueDto createDto) {
    }

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final SprintRepository sprintRepository;
    private final IssueTypeRepository issueTypeRepository;
    private final IssuePriorityRepository issuePriorityRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final IssueStatusHistoryRepository issueStatusHistoryRepository;
    private final ActivityLogService activityLogService;
    private final UserServiceFeignClient userServiceFeignClient;
    private final ObjectMapper objectMapper;
    private final ProjectSubscriptionSyncService projectSubscriptionSyncService;
    private final SubscriptionLimitService subscriptionLimitService;
    private final NotificationPublisher notificationPublisher;
    private final ActorNameResolver actorNameResolver;
    private final LabelRepository labelRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentService attachmentService;

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

    private AttachmentResponseDto toAttachmentDto(Attachment att) {
        if (att == null) return null;
        return AttachmentResponseDto.builder()
                .id(att.getId())
                .issueId(att.getIssueId())
                .fileId(att.getFileId())
                .fileName(att.getFileName())
                .fileUrl(att.getFileUrl())
                .fileType(att.getFileType())
                .fileSize(att.getFileSize())
                .uploadedBy(att.getUploadedBy())
                .uploadedAt(att.getUploadedAt())
                .build();
    }

    private IssueResponseDto toDto(Issue issue, Map<UUID, UserDetailDto> userMap, Map<UUID, WorkflowStatus> statusMap, List<AttachmentResponseDto> attachments) {
        WorkflowStatus status = issue.getStatusId() != null ? statusMap.get(issue.getStatusId()) : null;

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
                .labels(issue.getLabels().stream()
                        .map(l -> new com.iems.projectservice.dto.response.LabelDto(l.getId(), l.getName(), l.getColor()))
                        .collect(Collectors.toSet()))
                .statusName(status != null ? status.getName() : null)
                .statusCategory(status != null && status.getCategory() != null ? status.getCategory().name() : null)
                .attachments(attachments != null ? attachments : Collections.emptyList())
                .build();
    }

    private IssueResponseDto enrich(Issue issue) {
        Set<UUID> ids = new HashSet<>();
        if (issue.getAssigneeId() != null)
            ids.add(issue.getAssigneeId());
        if (issue.getReporterId() != null)
            ids.add(issue.getReporterId());
        Map<UUID, WorkflowStatus> statusMap = issue.getStatusId() != null
                ? workflowStatusRepository.findById(issue.getStatusId())
                        .map(status -> Map.of(status.getId(), status))
                        .orElse(Collections.emptyMap())
                : Collections.emptyMap();

        List<AttachmentResponseDto> attachments = attachmentRepository.findByIssueId(issue.getId())
                .stream()
                .map(this::toAttachmentDto)
                .collect(Collectors.toList());

        return toDto(issue, fetchUsers(ids), statusMap, attachments);
    }

    private List<IssueResponseDto> enrichList(List<Issue> issues) {
        Set<UUID> ids = new HashSet<>();
        Set<UUID> statusIds = new HashSet<>();
        List<UUID> issueIds = new ArrayList<>();
        for (Issue i : issues) {
            if (i.getAssigneeId() != null)
                ids.add(i.getAssigneeId());
            if (i.getReporterId() != null)
                ids.add(i.getReporterId());
            if (i.getStatusId() != null)
                statusIds.add(i.getStatusId());
            issueIds.add(i.getId());
        }
        Map<UUID, UserDetailDto> userMap = fetchUsers(ids);
        Map<UUID, WorkflowStatus> statusMap = statusIds.isEmpty()
                ? Collections.emptyMap()
                : workflowStatusRepository.findAllById(statusIds).stream()
                        .collect(Collectors.toMap(WorkflowStatus::getId, status -> status));

        Map<UUID, List<AttachmentResponseDto>> attachmentsByIssueId = new HashMap<>();
        if (!issueIds.isEmpty()) {
            List<Attachment> allAttachments = attachmentRepository.findByIssueIdIn(issueIds);
            for (Attachment att : allAttachments) {
                attachmentsByIssueId.computeIfAbsent(att.getIssueId(), k -> new ArrayList<>())
                        .add(toAttachmentDto(att));
            }
        }

        return issues.stream()
                .map(i -> toDto(i, userMap, statusMap, attachmentsByIssueId.get(i.getId())))
                .collect(Collectors.toList());
    }

    // ── Issue CRUD ───────────────────────────────────────────────────────────

    @Transactional
    public IssueResponseDto createIssue(UUID projectId, CreateIssueDto dto, UUID reporterId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.PROJECT_NOT_FOUND));

        // Check project lock after refreshing a stale owner subscription cache.
        project = projectSubscriptionSyncService.refreshProjectSubscription(project);
        if (project.isLocked()) {
            throw new AppException(ProjectErrorCode.PROJECT_LOCKED);
        }

        // Check issue limit based on project owner's subscription
        long count = issueRepository.countByProjectId(projectId);
        subscriptionLimitService.checkCanCreateIssue(count, project.getOwnerSubscription());
        String issueKey = buildIssueKey(project, nextIssueNumber(project));

        UUID defaultStatusId = resolveDefaultStatusId(projectId);

        int nextSortOrder = issueRepository.findMaxSortOrderByProjectId(projectId).orElse(0) + 1;

        Issue saved = createIssueEntity(projectId, dto, reporterId, issueKey, defaultStatusId, nextSortOrder, true);

        if (dto.getAttachments() != null && !dto.getAttachments().isEmpty()) {
            for (AttachmentRequestDto attDto : dto.getAttachments()) {
                attachmentService.addAttachment(
                        saved.getId(),
                        attDto.getFileId(),
                        attDto.getFileName(),
                        attDto.getFileUrl(),
                        attDto.getFileType(),
                        attDto.getFileSize(),
                        reporterId
                );
            }
        }

        // Notify assignee if different from reporter
        if (saved.getAssigneeId() != null && !saved.getAssigneeId().equals(reporterId)) {
            try {
                String reporterName = actorNameResolver.resolve(reporterId);
                notificationPublisher.notifyIssueAssigned(
                        saved.getAssigneeId(), reporterId, reporterName,
                        saved.getIssueKey(), saved.getTitle(), saved.getId(),
                        projectId, project.getName());
            } catch (Exception e) {
                log.warn("Failed to send issue assigned notification: {}", e.getMessage());
            }
        }

        return enrich(saved);
    }

    private UUID resolveDefaultStatusId(UUID projectId) {
        Workflow defaultWorkflow = workflowRepository.findByProjectIdAndIsDefaultTrue(projectId)
                .orElse(null);
        if (defaultWorkflow == null) {
            return null;
        }

        List<WorkflowStatus> statuses = workflowStatusRepository
                .findByWorkflowIdOrderBySortOrderAsc(defaultWorkflow.getId());
        return statuses.isEmpty() ? null : statuses.get(0).getId();
    }

    private Issue createIssueEntity(UUID projectId, CreateIssueDto dto, UUID reporterId, String issueKey,
            UUID defaultStatusId, int sortOrder, boolean logActivity) {
        Issue issue = new Issue();
        issue.setProjectId(projectId);
        issue.setIssueKey(issueKey);
        issue.setIssueTypeId(dto.getIssueTypeId());
        issue.setParentId(dto.getParentId());
        issue.setTitle(dto.getTitle().trim());
        issue.setDescription(normalizeNullableText(dto.getDescription()));
        issue.setStatusId(dto.getStatusId() != null ? dto.getStatusId() : defaultStatusId);
        issue.setPriorityId(dto.getPriorityId());
        issue.setAssigneeId(dto.getAssigneeId());
        issue.setReporterId(reporterId);
        issue.setSprintId(dto.getSprintId());
        issue.setStoryPoints(dto.getStoryPoints());
        issue.setSortOrder(sortOrder);
        issue.setDueDate(dto.getDueDate());

        if (dto.getLabelIds() != null && !dto.getLabelIds().isEmpty()) {
            List<Label> labels = labelRepository.findAllById(dto.getLabelIds());
            issue.setLabels(new HashSet<>(labels));
        }

        Issue saved = issueRepository.save(issue);

        if (logActivity) {
            activityLogService.log(projectId, saved.getId(), reporterId, "ISSUE_CREATED",
                    "Created issue " + issueKey + ": " + dto.getTitle());
        }

        return saved;
    }

    @Transactional
    public IssueResponseDto updateIssue(UUID issueId, UpdateIssueDto dto, UUID userId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.ISSUE_NOT_FOUND));

        List<String> changedFields = new ArrayList<>();

        if (dto.getTitle() != null) {
            String nextTitle = dto.getTitle().trim();
            if (nextTitle.isEmpty()) {
                throw new IllegalArgumentException("Title is required");
            }
            if (!Objects.equals(issue.getTitle(), nextTitle)) {
                issue.setTitle(nextTitle);
                changedFields.add("title");
            }
        }
        if (dto.isDescriptionSet() && !Objects.equals(issue.getDescription(), normalizeNullableText(dto.getDescription()))) {
            issue.setDescription(normalizeNullableText(dto.getDescription()));
            changedFields.add("description");
        }
        if (dto.getIssueTypeId() != null && !Objects.equals(issue.getIssueTypeId(), dto.getIssueTypeId())) {
            issue.setIssueTypeId(dto.getIssueTypeId());
            changedFields.add("issue type");
        }
        if (dto.isPriorityIdSet() && !Objects.equals(issue.getPriorityId(), dto.getPriorityId())) {
            issue.setPriorityId(dto.getPriorityId());
            changedFields.add("priority");
        }
        if (dto.isAssigneeIdSet()) {
            UUID previousAssigneeId = issue.getAssigneeId();
            UUID nextAssigneeId = dto.getAssigneeId();
            issue.setAssigneeId(nextAssigneeId);

            if (!Objects.equals(previousAssigneeId, nextAssigneeId)) {
                if (nextAssigneeId == null) {
                    activityLogService.log(issue.getProjectId(), issueId, userId, "ISSUE_ASSIGNED",
                            "Unassigned issue " + issue.getIssueKey());
                } else {
                    activityLogService.log(issue.getProjectId(), issueId, userId, "ISSUE_ASSIGNED",
                            "Assigned issue " + issue.getIssueKey());
                    // Notify new assignee
                    try {
                        var project = projectRepository.findById(issue.getProjectId()).orElse(null);
                        String projectName = project != null ? project.getName() : "";
                        String actorName = actorNameResolver.resolve(userId);
                        notificationPublisher.notifyIssueAssigned(
                                nextAssigneeId, userId, actorName,
                                issue.getIssueKey(), issue.getTitle(), issue.getId(),
                                issue.getProjectId(), projectName);
                    } catch (Exception e) {
                        log.warn("Failed to send issue assigned notification: {}", e.getMessage());
                    }
                }
            }
        }
        if (dto.isSprintIdSet() && !Objects.equals(issue.getSprintId(), dto.getSprintId())) {
            issue.setSprintId(dto.getSprintId());
            changedFields.add("sprint");
        }
        if (dto.isParentIdSet() && !Objects.equals(issue.getParentId(), dto.getParentId())) {
            issue.setParentId(dto.getParentId());
            changedFields.add("parent");
        }
        if (dto.isStoryPointsSet() && !Objects.equals(issue.getStoryPoints(), dto.getStoryPoints())) {
            issue.setStoryPoints(dto.getStoryPoints());
            changedFields.add("story points");
        }
        if (dto.getSortOrder() != null && !Objects.equals(issue.getSortOrder(), dto.getSortOrder())) {
            issue.setSortOrder(dto.getSortOrder());
            changedFields.add("sort order");
        }
        if (dto.isDueDateSet() && !Objects.equals(issue.getDueDate(), dto.getDueDate())) {
            issue.setDueDate(dto.getDueDate());
            changedFields.add("due date");
        }

        if (dto.getLabelIds() != null) {
            List<Label> labels = labelRepository.findAllById(dto.getLabelIds());
            issue.setLabels(new HashSet<>(labels));
            changedFields.add("labels");
        }

        if (dto.getStatusId() != null && !dto.getStatusId().equals(issue.getStatusId())) {
            changeStatus(issue, dto.getStatusId(), userId);
        }

        if (dto.getAttachments() != null) {
            List<Attachment> existing = attachmentRepository.findByIssueId(issueId);
            Set<String> newFileIds = dto.getAttachments().stream()
                    .map(AttachmentRequestDto::getFileId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            for (Attachment att : existing) {
                if (!newFileIds.contains(att.getFileId())) {
                    attachmentRepository.delete(att);
                }
            }

            Set<String> existingFileIds = existing.stream()
                    .map(Attachment::getFileId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            for (AttachmentRequestDto attDto : dto.getAttachments()) {
                if (!existingFileIds.contains(attDto.getFileId())) {
                    attachmentService.addAttachment(
                            issueId,
                            attDto.getFileId(),
                            attDto.getFileName(),
                            attDto.getFileUrl(),
                            attDto.getFileType(),
                            attDto.getFileSize(),
                            userId
                    );
                }
            }
            changedFields.add("attachments");
        }

        Issue saved = issueRepository.save(issue);
        if (!changedFields.isEmpty()) {
            activityLogService.log(
                    saved.getProjectId(),
                    saved.getId(),
                    userId,
                    "ISSUE_UPDATED",
                    saved.getIssueKey() + ": Updated " + String.join(", ", changedFields));
        }

        return enrich(saved);
    }

    private void changeStatus(Issue issue, UUID newStatusId, UUID userId) {
        UUID fromStatusId = issue.getStatusId();

        String fromName = issue.getStatusId() != null
                ? workflowStatusRepository.findById(issue.getStatusId())
                        .map(WorkflowStatus::getName).orElse("Unknown")
                : "None";
        String toName = workflowStatusRepository.findById(newStatusId)
                .map(WorkflowStatus::getName).orElse("Unknown");

        issue.setStatusId(newStatusId);

        IssueStatusHistory history = new IssueStatusHistory();
        history.setProjectId(issue.getProjectId());
        history.setIssueId(issue.getId());
        history.setSprintId(issue.getSprintId());
        history.setFromStatusId(fromStatusId);
        history.setToStatusId(newStatusId);
        history.setStoryPoints(issue.getStoryPoints());
        history.setChangedBy(userId);
        issueStatusHistoryRepository.save(history);

        activityLogService.log(
                issue.getProjectId(),
                issue.getId(),
                userId,
                "ISSUE_STATUS_CHANGED",
                issue.getIssueKey() + ": " + fromName + " → " + toName);
    }

    @Transactional
    public void deleteIssue(UUID issueId, UUID userId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.ISSUE_NOT_FOUND));
        activityLogService.log(issue.getProjectId(), issueId, userId, "ISSUE_DELETED",
                "Deleted issue " + issue.getIssueKey() + ": " + issue.getTitle());
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

    public PagedResponseDto<IssueResponseDto> getIssuesByProjectPaged(UUID projectId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 200));
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "sortOrder"));
        Page<Issue> result = issueRepository.findByProjectId(projectId, pageable);

        return new PagedResponseDto<>(
                enrichList(result.getContent()),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isLast());
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

    @Transactional
    public IssueImportResultDto importIssuesFromExcel(UUID projectId, MultipartFile file, UUID reporterId) {
        validateImportFile(file);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.PROJECT_NOT_FOUND));

        List<IssueType> issueTypes = issueTypeRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        Map<String, UUID> issueTypeByName = issueTypes.stream()
                .collect(Collectors.toMap(i -> normalizeKey(i.getName()), IssueType::getId, (a, b) -> a));

        List<IssuePriority> priorities = issuePriorityRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        Map<String, UUID> priorityByName = priorities.stream()
                .collect(Collectors.toMap(i -> normalizeKey(i.getName()), IssuePriority::getId, (a, b) -> a));

        Map<String, UUID> sprintByName = sprintRepository.findByProjectIdOrderBySortOrderAsc(projectId).stream()
                .collect(Collectors.toMap(s -> normalizeKey(s.getName()), Sprint::getId, (a, b) -> a));

        Map<String, UUID> assigneeByEmail = resolveAssigneeByEmail(projectId);

        List<ParsedIssueImportRow> rows = parseImportRows(file, issueTypeByName, priorityByName, assigneeByEmail,
                sprintByName);
        if (rows.isEmpty()) {
            throw new AppException(ProjectErrorCode.ISSUE_IMPORT_FILE_INVALID,
                    "Excel file does not contain any data rows");
        }

        int insertedCount = 0;
        int updatedCount = 0;
        int unchangedCount = 0;
        int nextIssueNumber = nextIssueNumber(project);
        int nextSortOrder = issueRepository.findMaxSortOrderByProjectId(projectId).orElse(0) + 1;
        UUID defaultStatusId = resolveDefaultStatusId(projectId);

        for (ParsedIssueImportRow row : rows) {
            String issueKey = row.issueKey();

            if (!issueKey.isBlank()) {
                Optional<Issue> existingOpt = issueRepository.findByProjectIdAndIssueKey(projectId, issueKey);
                if (existingOpt.isPresent()) {
                    Issue existing = existingOpt.get();
                    if (applyImportedChanges(existing, row.createDto(), reporterId)) {
                        issueRepository.save(existing);
                        updatedCount++;
                        activityLogService.log(projectId, existing.getId(), reporterId, "ISSUE_IMPORTED_UPDATED",
                                "Updated issue " + existing.getIssueKey() + " from Excel import");
                    } else {
                        unchangedCount++;
                    }
                    continue;
                }
            }

            String newIssueKey = buildIssueKey(project, nextIssueNumber++);
            createIssueEntity(projectId, row.createDto(), reporterId, newIssueKey, defaultStatusId, nextSortOrder++,
                    true);
            insertedCount++;
        }

        return IssueImportResultDto.builder()
                .totalRows(rows.size())
                .insertedCount(insertedCount)
                .updatedCount(updatedCount)
                .unchangedCount(unchangedCount)
                .message("Import completed: " + insertedCount + " inserted, " + updatedCount + " updated, "
                        + unchangedCount + " unchanged")
                .build();
    }

    private int nextIssueNumber(Project project) {
        return issueRepository.findMaxIssueNumberByProjectIdAndProjectKey(project.getId(), project.getProjectKey()) + 1;
    }

    private String buildIssueKey(Project project, int issueNumber) {
        return project.getProjectKey() + "-" + issueNumber;
    }

    public byte[] exportIssuesToExcel(UUID projectId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.PROJECT_NOT_FOUND));

        List<Issue> issues = issueRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        List<IssueType> issueTypes = issueTypeRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        List<IssuePriority> priorities = issuePriorityRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        List<Sprint> sprints = sprintRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        List<String> assigneeEmails = resolveAssigneeEmails(projectId);

        Map<UUID, String> issueTypeNames = issueTypeRepository.findByProjectIdOrderBySortOrderAsc(projectId).stream()
                .collect(Collectors.toMap(IssueType::getId, IssueType::getName, (a, b) -> a));
        Map<UUID, String> priorityNames = issuePriorityRepository.findByProjectIdOrderBySortOrderAsc(projectId).stream()
                .collect(Collectors.toMap(IssuePriority::getId, IssuePriority::getName, (a, b) -> a));
        Map<UUID, String> sprintNames = sprintRepository.findByProjectIdOrderBySortOrderAsc(projectId).stream()
                .collect(Collectors.toMap(Sprint::getId, Sprint::getName, (a, b) -> a));

        Set<UUID> userIds = issues.stream()
                .map(Issue::getAssigneeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, UserDetailDto> userMap = fetchUsers(userIds);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Issue Import");

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < IMPORT_HEADERS.length; i++) {
                headerRow.createCell(i).setCellValue(IMPORT_HEADERS[i]);
            }

            int rowIndex = 1;
            for (Issue issue : issues) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(nullToEmpty(issue.getIssueKey()));
                row.createCell(1).setCellValue(nullToEmpty(issueTypeNames.get(issue.getIssueTypeId())));
                row.createCell(2).setCellValue(nullToEmpty(issue.getTitle()));
                row.createCell(3).setCellValue(nullToEmpty(issue.getDescription()));
                row.createCell(4).setCellValue(nullToEmpty(priorityNames.get(issue.getPriorityId())));

                String assigneeEmail = "";
                if (issue.getAssigneeId() != null) {
                    UserDetailDto user = userMap.get(issue.getAssigneeId());
                    assigneeEmail = user != null ? nullToEmpty(user.getEmail()) : "";
                }
                row.createCell(5).setCellValue(assigneeEmail);
                row.createCell(6).setCellValue(nullToEmpty(sprintNames.get(issue.getSprintId())));

                if (issue.getStoryPoints() != null) {
                    row.createCell(7).setCellValue(issue.getStoryPoints());
                } else {
                    row.createCell(7).setBlank();
                }
                row.createCell(8).setCellValue(issue.getDueDate() != null ? issue.getDueDate().toString() : "");
            }

            List<String> issueTypeOptions = issueTypes.stream()
                    .map(IssueType::getName)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();

            List<String> priorityOptions = priorities.stream()
                    .map(IssuePriority::getName)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();

            List<String> sprintOptions = sprints.stream()
                    .map(Sprint::getName)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();

            addTemplateDropdowns(workbook, sheet, issueTypeOptions, priorityOptions, assigneeEmails, sprintOptions);

            for (int i = 0; i < IMPORT_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.setColumnHidden(0, true);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new AppException(ProjectErrorCode.INTERNAL_SERVER_ERROR, "Failed to export issues to Excel");
        }
    }

    public byte[] generateImportTemplate(UUID projectId) {
        List<IssueType> issueTypes = issueTypeRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        List<IssuePriority> priorities = issuePriorityRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        List<Sprint> sprints = sprintRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        List<String> assigneeEmails = resolveAssigneeEmails(projectId);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Issue Import");

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < IMPORT_HEADERS.length; i++) {
                headerRow.createCell(i).setCellValue(IMPORT_HEADERS[i]);
            }

            List<String> issueTypeOptions = issueTypes.stream()
                    .map(IssueType::getName)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();

            List<String> priorityOptions = priorities.stream()
                    .map(IssuePriority::getName)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();

            List<String> sprintOptions = sprints.stream()
                    .map(Sprint::getName)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();

            addTemplateDropdowns(workbook, sheet, issueTypeOptions, priorityOptions, assigneeEmails, sprintOptions);

            for (int i = 0; i < IMPORT_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.setColumnHidden(0, true);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new AppException(ProjectErrorCode.ISSUE_IMPORT_TEMPLATE_INVALID,
                    "Failed to generate import template");
        }
    }

    private void validateImportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ProjectErrorCode.ISSUE_IMPORT_FILE_INVALID, "Excel file is required");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new AppException(ProjectErrorCode.ISSUE_IMPORT_FILE_INVALID, "Only .xlsx files are supported");
        }
    }

    private List<ParsedIssueImportRow> parseImportRows(
            MultipartFile file,
            Map<String, UUID> issueTypeByName,
            Map<String, UUID> priorityByName,
            Map<String, UUID> assigneeByEmail,
            Map<String, UUID> sprintByName) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            validateHeader(sheet);

            List<ParsedIssueImportRow> rows = new ArrayList<>();
            for (int rowIndex = IMPORT_DATA_START_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isEmptyRow(row)) {
                    continue;
                }
                rows.add(mapRow(row, rowIndex + 1, issueTypeByName, priorityByName, assigneeByEmail, sprintByName));
            }
            return rows;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ProjectErrorCode.ISSUE_IMPORT_FILE_INVALID,
                    "Failed to parse Excel file: " + e.getMessage());
        }
    }

    private void validateHeader(Sheet sheet) {
        Row header = sheet.getRow(0);
        if (header == null) {
            throw new AppException(ProjectErrorCode.ISSUE_IMPORT_FILE_INVALID, "Missing header row");
        }

        DataFormatter formatter = new DataFormatter();
        for (int i = 0; i < IMPORT_HEADERS.length; i++) {
            String actual = formatter.formatCellValue(header.getCell(i)).trim();
            if (!IMPORT_HEADERS[i].equalsIgnoreCase(actual)) {
                throw new AppException(ProjectErrorCode.ISSUE_IMPORT_FILE_INVALID,
                        "Invalid template header at column " + (i + 1) + ". Expected '" + IMPORT_HEADERS[i] + "'");
            }
        }
    }

    private boolean isEmptyRow(Row row) {
        if (row == null) {
            return true;
        }
        DataFormatter formatter = new DataFormatter();
        for (int i = 0; i < IMPORT_HEADERS.length; i++) {
            String value = formatter.formatCellValue(row.getCell(i)).trim();
            if (!value.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private ParsedIssueImportRow mapRow(
            Row row,
            int rowNumber,
            Map<String, UUID> issueTypeByName,
            Map<String, UUID> priorityByName,
            Map<String, UUID> assigneeByEmail,
            Map<String, UUID> sprintByName) {
        String issueKey = getStringCellValue(row, 0);
        String issueTypeName = getStringCellValue(row, 1);
        String title = getStringCellValue(row, 2);
        String description = getStringCellValue(row, 3);
        String priorityName = getStringCellValue(row, 4);
        String assigneeEmail = getStringCellValue(row, 5);
        String sprintName = getStringCellValue(row, 6);
        String storyPointsRaw = getStringCellValue(row, 7);
        String dueDateRaw = getStringCellValue(row, 8);

        if (issueTypeName.isBlank()) {
            throw new AppException(ProjectErrorCode.ISSUE_IMPORT_ROW_INVALID,
                    "Row " + rowNumber + ": Issue Type is required");
        }
        if (title.isBlank()) {
            throw new AppException(ProjectErrorCode.ISSUE_IMPORT_ROW_INVALID,
                    "Row " + rowNumber + ": Title is required");
        }

        UUID issueTypeId = issueTypeByName.get(normalizeKey(issueTypeName));
        if (issueTypeId == null) {
            throw new AppException(ProjectErrorCode.ISSUE_IMPORT_ROW_INVALID,
                    "Row " + rowNumber + ": Unknown issue type '" + issueTypeName + "'");
        }

        UUID priorityId = null;
        if (!priorityName.isBlank()) {
            priorityId = priorityByName.get(normalizeKey(priorityName));
            if (priorityId == null) {
                throw new AppException(ProjectErrorCode.ISSUE_IMPORT_ROW_INVALID,
                        "Row " + rowNumber + ": Unknown priority '" + priorityName + "'");
            }
        }

        UUID assigneeId = null;
        if (!assigneeEmail.isBlank()) {
            assigneeId = assigneeByEmail.get(normalizeKey(assigneeEmail));
            if (assigneeId == null) {
                throw new AppException(ProjectErrorCode.ISSUE_IMPORT_ROW_INVALID,
                        "Row " + rowNumber + ": Assignee email not found in project members: '" + assigneeEmail + "'");
            }
        }

        UUID sprintId = null;
        if (!sprintName.isBlank()) {
            sprintId = sprintByName.get(normalizeKey(sprintName));
            if (sprintId == null) {
                throw new AppException(ProjectErrorCode.ISSUE_IMPORT_ROW_INVALID,
                        "Row " + rowNumber + ": Sprint not found: '" + sprintName + "'");
            }
        }

        Integer storyPoints = null;
        if (!storyPointsRaw.isBlank()) {
            try {
                storyPoints = Integer.parseInt(storyPointsRaw);
            } catch (NumberFormatException ex) {
                throw new AppException(ProjectErrorCode.ISSUE_IMPORT_ROW_INVALID,
                        "Row " + rowNumber + ": Story Points must be an integer");
            }
        }

        LocalDate dueDate = null;
        if (!dueDateRaw.isBlank()) {
            try {
                dueDate = LocalDate.parse(dueDateRaw, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ex) {
                throw new AppException(ProjectErrorCode.ISSUE_IMPORT_ROW_INVALID,
                        "Row " + rowNumber + ": Due Date must use yyyy-MM-dd format");
            }
        }

        CreateIssueDto dto = new CreateIssueDto();
        dto.setIssueTypeId(issueTypeId);
        dto.setTitle(title);
        dto.setDescription(description.isBlank() ? null : description);
        dto.setPriorityId(priorityId);
        dto.setAssigneeId(assigneeId);
        dto.setSprintId(sprintId);
        dto.setStoryPoints(storyPoints);
        dto.setDueDate(dueDate);
        return new ParsedIssueImportRow(issueKey, dto);
    }

    private boolean applyImportedChanges(Issue issue, CreateIssueDto dto, UUID userId) {
        boolean changed = false;

        if (!Objects.equals(issue.getIssueTypeId(), dto.getIssueTypeId())) {
            issue.setIssueTypeId(dto.getIssueTypeId());
            changed = true;
        }
        if (!Objects.equals(nullToEmpty(issue.getTitle()), nullToEmpty(dto.getTitle()))) {
            issue.setTitle(dto.getTitle());
            changed = true;
        }
        if (!Objects.equals(nullToEmpty(issue.getDescription()), nullToEmpty(dto.getDescription()))) {
            issue.setDescription(dto.getDescription());
            changed = true;
        }
        if (!Objects.equals(issue.getPriorityId(), dto.getPriorityId())) {
            issue.setPriorityId(dto.getPriorityId());
            changed = true;
        }
        if (!Objects.equals(issue.getAssigneeId(), dto.getAssigneeId())) {
            issue.setAssigneeId(dto.getAssigneeId());
            changed = true;
        }
        if (!Objects.equals(issue.getSprintId(), dto.getSprintId())) {
            issue.setSprintId(dto.getSprintId());
            changed = true;
        }
        if (!Objects.equals(issue.getStoryPoints(), dto.getStoryPoints())) {
            issue.setStoryPoints(dto.getStoryPoints());
            changed = true;
        }
        if (!Objects.equals(issue.getDueDate(), dto.getDueDate())) {
            issue.setDueDate(dto.getDueDate());
            changed = true;
        }

        if (changed) {
            issue.setReporterId(userId);
        }
        return changed;
    }

    private String getStringCellValue(Row row, int col) {
        DataFormatter formatter = new DataFormatter();
        String value = formatter.formatCellValue(row.getCell(col));
        return value == null ? "" : value.trim();
    }

    private Map<String, UUID> resolveAssigneeByEmail(UUID projectId) {
        List<ProjectMember> members = projectMemberRepository.findByProjectId(projectId);
        Set<UUID> accountIds = members.stream()
                .map(ProjectMember::getAccountId)
                .collect(Collectors.toSet());

        Map<UUID, UserDetailDto> userMap = fetchUsers(accountIds);
        Map<String, UUID> emailMap = new HashMap<>();
        for (ProjectMember member : members) {
            UserDetailDto user = userMap.get(member.getAccountId());
            if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
                emailMap.put(normalizeKey(user.getEmail()), member.getAccountId());
            }
        }
        return emailMap;
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String normalizeNullableText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value;
    }

    private List<String> resolveAssigneeEmails(UUID projectId) {
        List<ProjectMember> members = projectMemberRepository.findByProjectId(projectId);
        Set<UUID> accountIds = members.stream()
                .map(ProjectMember::getAccountId)
                .collect(Collectors.toSet());

        Map<UUID, UserDetailDto> userMap = fetchUsers(accountIds);
        return members.stream()
                .map(ProjectMember::getAccountId)
                .map(userMap::get)
                .filter(Objects::nonNull)
                .map(UserDetailDto::getEmail)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private void addTemplateDropdowns(
            Workbook workbook,
            Sheet mainSheet,
            List<String> issueTypeOptions,
            List<String> priorityOptions,
            List<String> assigneeOptions,
            List<String> sprintOptions) {
        Sheet optionsSheet = workbook.createSheet("_options");

        int issueTypeCount = writeOptions(optionsSheet, 0, issueTypeOptions);
        int priorityCount = writeOptions(optionsSheet, 1, priorityOptions);
        int assigneeCount = writeOptions(optionsSheet, 2, assigneeOptions);
        int sprintCount = writeOptions(optionsSheet, 3, sprintOptions);

        String issueTypeRange = defineNamedRange(workbook, "IssueTypeOptions", "_options", 0, issueTypeCount);
        String priorityRange = defineNamedRange(workbook, "PriorityOptions", "_options", 1, priorityCount);
        String assigneeRange = defineNamedRange(workbook, "AssigneeEmailOptions", "_options", 2, assigneeCount);
        String sprintRange = defineNamedRange(workbook, "SprintOptions", "_options", 3, sprintCount);

        addDropdownValidation(mainSheet, issueTypeRange, 1);
        addDropdownValidation(mainSheet, priorityRange, 4);
        addDropdownValidation(mainSheet, assigneeRange, 5);
        addDropdownValidation(mainSheet, sprintRange, 6);

        workbook.setSheetHidden(workbook.getSheetIndex(optionsSheet), true);
    }

    private int writeOptions(Sheet optionsSheet, int colIndex, List<String> options) {
        if (options == null || options.isEmpty()) {
            return 0;
        }

        for (int i = 0; i < options.size(); i++) {
            Row row = optionsSheet.getRow(i);
            if (row == null) {
                row = optionsSheet.createRow(i);
            }
            row.createCell(colIndex).setCellValue(options.get(i));
        }
        return options.size();
    }

    private String defineNamedRange(Workbook workbook, String rangeName, String optionsSheetName, int optionsColIndex,
            int optionsCount) {
        if (optionsCount <= 0) {
            return null;
        }

        String optionsCol = CellReference.convertNumToColString(optionsColIndex);
        String formula = "'" + optionsSheetName + "'!$" + optionsCol + "$1:$" + optionsCol + "$" + optionsCount;

        Name namedRange = workbook.getName(rangeName);
        if (namedRange == null) {
            namedRange = workbook.createName();
            namedRange.setNameName(rangeName);
        }
        namedRange.setRefersToFormula(formula);
        return rangeName;
    }

    private void addDropdownValidation(Sheet mainSheet, String formulaOrRangeName, int targetColIndex) {
        if (formulaOrRangeName == null || formulaOrRangeName.isBlank()) {
            return;
        }

        DataValidationHelper helper = mainSheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createFormulaListConstraint(formulaOrRangeName);
        CellRangeAddressList addressList = new CellRangeAddressList(
                IMPORT_DATA_START_ROW_INDEX,
                IMPORT_DATA_END_ROW_INDEX,
                targetColIndex,
                targetColIndex);

        DataValidation validation = helper.createValidation(constraint, addressList);
        validation.setShowErrorBox(true);
        if (validation instanceof XSSFDataValidation) {
            validation.setSuppressDropDownArrow(true);
        } else {
            validation.setSuppressDropDownArrow(false);
        }
        mainSheet.addValidationData(validation);
    }

    // ── IssueType CRUD ───────────────────────────────────────────────────────

    public List<IssueType> getIssueTypes(UUID projectId) {
        return issueTypeRepository.findByProjectIdOrderBySortOrderAsc(projectId);
    }

    public IssueType createIssueType(UUID projectId, String name, String description, String iconUrl) {
        int sortOrder = (int) issueTypeRepository.countByProjectId(projectId);
        return createIssueTypeWithSortOrder(projectId, name, description, iconUrl, sortOrder);
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

    @Transactional
    public List<IssueType> syncIssueTypes(UUID projectId, List<IssueTypeSyncItemDto> items) {
        List<IssueTypeSyncItemDto> safeItems = items != null ? items : List.of();
        List<IssueType> existing = issueTypeRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        Map<UUID, IssueType> existingById = existing.stream().collect(Collectors.toMap(IssueType::getId, i -> i));

        int sortOrder = 0;
        for (IssueTypeSyncItemDto item : safeItems) {
            if (item == null) {
                continue;
            }
            boolean removed = Boolean.TRUE.equals(item.getRemoved());

            if (removed && item.getId() != null) {
                deleteIssueType(item.getId());
                continue;
            }

            if (item.getId() != null) {
                IssueType issueType = existingById.get(item.getId());
                if (issueType == null || !issueType.getProjectId().equals(projectId)) {
                    throw new AppException(ProjectErrorCode.ISSUE_TYPE_NOT_FOUND);
                }
                if (item.getName() != null && !item.getName().trim().isEmpty()) {
                    issueType.setName(item.getName().trim());
                }
                issueType.setDescription(item.getDescription());
                issueType.setIconUrl(item.getIconUrl());
                issueType.setSortOrder(sortOrder++);
                issueTypeRepository.save(issueType);
                continue;
            }

            if (item.getName() == null || item.getName().trim().isEmpty()) {
                continue;
            }

            IssueType created = new IssueType();
            created.setProjectId(projectId);
            created.setName(item.getName().trim());
            created.setDescription(item.getDescription());
            created.setIconUrl(item.getIconUrl());
            created.setSortOrder(sortOrder++);
            issueTypeRepository.save(created);
        }

        return issueTypeRepository.findByProjectIdOrderBySortOrderAsc(projectId);
    }

    // ── IssuePriority CRUD ───────────────────────────────────────────────────

    public List<IssuePriority> getIssuePriorities(UUID projectId) {
        return issuePriorityRepository.findByProjectIdOrderBySortOrderAsc(projectId);
    }

    public IssuePriority createIssuePriority(UUID projectId, String name, String iconUrl, String color, UUID userId) {
        int sortOrder = (int) issuePriorityRepository.countByProjectId(projectId);
        IssuePriority priority = createIssuePriorityWithSortOrder(projectId, name, iconUrl, color, sortOrder);
        activityLogService.log(projectId, null, userId, "ISSUE_PRIORITY_CREATED",
                "Created issue priority: " + priority.getName());
        return priority;
    }

    public IssuePriority updateIssuePriority(UUID id, String name, String iconUrl, String color, UUID userId) {
        IssuePriority ip = issuePriorityRepository.findById(id)
                .orElseThrow(() -> new AppException(ProjectErrorCode.ISSUE_PRIORITY_NOT_FOUND));
        String oldName = ip.getName();
        List<String> changedFields = new ArrayList<>();
        if (name != null && !Objects.equals(ip.getName(), name)) {
            ip.setName(name);
            changedFields.add("name");
        }
        if (iconUrl != null && !Objects.equals(ip.getIconUrl(), iconUrl)) {
            ip.setIconUrl(iconUrl);
            changedFields.add("icon");
        }
        if (color != null && !Objects.equals(ip.getColor(), color)) {
            ip.setColor(color);
            changedFields.add("color");
        }
        IssuePriority saved = issuePriorityRepository.save(ip);
        if (!changedFields.isEmpty()) {
            activityLogService.log(saved.getProjectId(), null, userId, "ISSUE_PRIORITY_UPDATED",
                    "Updated issue priority " + oldName + ": " + String.join(", ", changedFields));
        }
        return saved;
    }

    public void deleteIssuePriority(UUID id, UUID userId) {
        IssuePriority ip = issuePriorityRepository.findById(id)
                .orElseThrow(() -> new AppException(ProjectErrorCode.ISSUE_PRIORITY_NOT_FOUND));
        activityLogService.log(ip.getProjectId(), null, userId, "ISSUE_PRIORITY_DELETED",
                "Deleted issue priority: " + ip.getName());
        issuePriorityRepository.delete(ip);
    }

    @Transactional
    public List<IssuePriority> syncIssuePriorities(UUID projectId, List<IssuePrioritySyncItemDto> items, UUID userId) {
        List<IssuePrioritySyncItemDto> safeItems = items != null ? items : List.of();
        List<IssuePriority> existing = issuePriorityRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        Map<UUID, IssuePriority> existingById = existing.stream()
                .collect(Collectors.toMap(IssuePriority::getId, i -> i));

        int sortOrder = 0;
        int createdCount = 0;
        int updatedCount = 0;
        int deletedCount = 0;
        for (IssuePrioritySyncItemDto item : safeItems) {
            if (item == null) {
                continue;
            }
            boolean removed = Boolean.TRUE.equals(item.getRemoved());

            if (removed && item.getId() != null) {
                IssuePriority priority = existingById.get(item.getId());
                if (priority == null || !priority.getProjectId().equals(projectId)) {
                    throw new AppException(ProjectErrorCode.ISSUE_PRIORITY_NOT_FOUND);
                }
                issuePriorityRepository.delete(priority);
                deletedCount++;
                continue;
            }

            if (item.getId() != null) {
                IssuePriority priority = existingById.get(item.getId());
                if (priority == null || !priority.getProjectId().equals(projectId)) {
                    throw new AppException(ProjectErrorCode.ISSUE_PRIORITY_NOT_FOUND);
                }
                boolean changed = false;
                if (item.getName() != null && !item.getName().trim().isEmpty()) {
                    String nextName = item.getName().trim();
                    changed = changed || !Objects.equals(priority.getName(), nextName);
                    priority.setName(nextName);
                }
                changed = changed || !Objects.equals(priority.getIconUrl(), item.getIconUrl());
                changed = changed || !Objects.equals(priority.getColor(), item.getColor());
                changed = changed || !Objects.equals(priority.getSortOrder(), sortOrder);
                priority.setIconUrl(item.getIconUrl());
                priority.setColor(item.getColor());
                priority.setSortOrder(sortOrder++);
                issuePriorityRepository.save(priority);
                if (changed) {
                    updatedCount++;
                }
                continue;
            }

            if (item.getName() == null || item.getName().trim().isEmpty()) {
                continue;
            }

            IssuePriority created = new IssuePriority();
            created.setProjectId(projectId);
            created.setName(item.getName().trim());
            created.setIconUrl(item.getIconUrl());
            created.setColor(item.getColor());
            created.setSortOrder(sortOrder++);
            issuePriorityRepository.save(created);
            createdCount++;
        }

        if (createdCount > 0 || updatedCount > 0 || deletedCount > 0) {
            activityLogService.log(projectId, null, userId, "ISSUE_PRIORITIES_SYNCED",
                    "Synced issue priorities: " + createdCount + " created, "
                            + updatedCount + " updated, " + deletedCount + " deleted");
        }

        return issuePriorityRepository.findByProjectIdOrderBySortOrderAsc(projectId);
    }

    @Transactional
    public void createDefaultIssueTypes(UUID projectId) {
        int nextSortOrder = (int) issueTypeRepository.countByProjectId(projectId);
        createIssueTypeWithSortOrder(projectId, "EPIC", "A large body of work", null, nextSortOrder++);
        createIssueTypeWithSortOrder(projectId, "STORY", "A user story", null, nextSortOrder++);
        createIssueTypeWithSortOrder(projectId, "TASK", "A task to be done", null, nextSortOrder++);
        createIssueTypeWithSortOrder(projectId, "BUG", "A bug to be fixed", null, nextSortOrder++);
        createIssueTypeWithSortOrder(projectId, "SUBTASK", "A subtask", null, nextSortOrder);
    }

    @Transactional
    public void createDefaultPriorities(UUID projectId) {
        int nextSortOrder = (int) issuePriorityRepository.countByProjectId(projectId);
        createIssuePriorityWithSortOrder(projectId, "Highest", null, "#FF0000", nextSortOrder++);
        createIssuePriorityWithSortOrder(projectId, "High", null, "#FF6B00", nextSortOrder++);
        createIssuePriorityWithSortOrder(projectId, "Medium", null, "#FFAB00", nextSortOrder++);
        createIssuePriorityWithSortOrder(projectId, "Low", null, "#2684FF", nextSortOrder++);
        createIssuePriorityWithSortOrder(projectId, "Lowest", null, "#0065FF", nextSortOrder);
    }

    private IssueType createIssueTypeWithSortOrder(UUID projectId, String name, String description, String iconUrl,
            int sortOrder) {
        IssueType it = new IssueType();
        it.setProjectId(projectId);
        it.setName(name);
        it.setDescription(description);
        it.setIconUrl(iconUrl);
        it.setSortOrder(sortOrder);
        return issueTypeRepository.save(it);
    }

    private IssuePriority createIssuePriorityWithSortOrder(UUID projectId, String name, String iconUrl, String color,
            int sortOrder) {
        IssuePriority ip = new IssuePriority();
        ip.setProjectId(projectId);
        ip.setName(name);
        ip.setIconUrl(iconUrl);
        ip.setColor(color);
        ip.setSortOrder(sortOrder);
        return issuePriorityRepository.save(ip);
    }
}
