package com.iems.projectservice.service.Impl;

import com.iems.projectservice.client.DocumentServiceFeignClient;
import com.iems.projectservice.dto.request.UserIdsDto;
import com.iems.projectservice.dto.response.*;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.service.ProjectService;
import com.iems.projectservice.dto.*;
import com.iems.projectservice.dto.request.ProjectIdsDto;
import com.iems.projectservice.entity.Task;
import com.iems.projectservice.entity.TaskAttachment;
import com.iems.projectservice.entity.TaskStatusHistory;
import com.iems.projectservice.entity.enums.TaskStatus;
import com.iems.projectservice.repository.TaskAttachmentRepository;
import com.iems.projectservice.repository.TaskCommentRepository;
import com.iems.projectservice.repository.TaskRepository;
import com.iems.projectservice.repository.TaskStatusHistoryRepository;
import com.iems.projectservice.service.ITaskService;
import com.iems.projectservice.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.entity.TaskComment;

@Slf4j
@Service
@Transactional
public class TaskService implements ITaskService {
    @Autowired
    private TaskRepository taskRepository;
    
    @Autowired
    private TaskStatusHistoryRepository statusHistoryRepository;
    
    @Autowired
    @Lazy
    private ProjectService projectService;

    @Autowired
    private DocumentServiceFeignClient documentServiceFeignClient;

    @Autowired
    private TaskCommentRepository taskCommentRepository;

    @Autowired
    private TaskAttachmentRepository taskAttachmentRepository;
    
    @Autowired
    private IUserService userService;


    // UC23: Tạo Nhiệm vụ
    @Override
    public TaskResponseDto createTask(CreateTaskDto createDto) {
        // Validate dates
        UUID userId = userService.getUserIdFromRequest();
        if (createDto.getStartDate() != null && createDto.getDueDate() != null) {
            if (createDto.getStartDate().isAfter(createDto.getDueDate())) {
                throw new AppException(ProjectErrorCode.INVALID_REQUEST);
            }
        }
        
        if (createDto.getDueDate() != null && createDto.getDueDate().isBefore(LocalDate.now())) {
            throw new AppException(ProjectErrorCode.INVALID_REQUEST);
        }

        Task task = new Task();
        task.setProjectId(createDto.getProjectId());
        task.setTitle(createDto.getTitle());
        task.setDescription(createDto.getDescription());
        task.setAssignedTo(createDto.getAssignedTo());
        task.setCreatedBy(userId);
        task.setStatus(TaskStatus.TO_DO); // Default status using enum
        task.setPriority(createDto.getPriority()); // Using enum directly
        task.setTaskType(createDto.getTaskType());
        // Validate parentTaskId by business rule
        if (createDto.getParentTaskId() != null) {
            UUID parentId = createDto.getParentTaskId();
//            Task parent = taskRepository.findById(parentId)
//                    .orElseThrow(() -> new AppException(ProjectErrorCode.TASK_NOT_FOUND));
//            if (TaskType.EPIC.equals(parent.getTaskType()) || TaskType.TASK.equals(parent.getTaskType()) || TaskType.STORY.equals(parent.getTaskType()) || TaskType.BUG.equals(parent.getTaskType())) {
//                // Allow any type to be a parent for now; customize if needed
//            }
            task.setParentTaskId(parentId);
        }
        task.setStartDate(createDto.getStartDate());
        task.setDueDate(createDto.getDueDate());
        task.setPhaseId(createDto.getPhaseId());
        task.setCreatedBy(userId);
        task.setUpdatedBy(userId);
        
        Task savedTask = taskRepository.save(task);
        
        // Create initial status history
        createStatusHistory(savedTask, TaskStatus.TO_DO, userId, "Task created");
        
        return convertToResponseDto(savedTask);
    }

    // UC23: Tạo Nhiệm vụ với tệp đính kèm
    public TaskResponseDto createTask(CreateTaskDto createDto, MultipartFile[] files) {
        // Validate dates
        UUID userId = userService.getUserIdFromRequest();
        if (createDto.getStartDate() != null && createDto.getDueDate() != null) {
            if (createDto.getStartDate().isAfter(createDto.getDueDate())) {
                throw new AppException(ProjectErrorCode.INVALID_REQUEST);
            }
        }
        
        if (createDto.getDueDate() != null && createDto.getDueDate().isBefore(LocalDate.now())) {
            throw new AppException(ProjectErrorCode.INVALID_REQUEST);
        }

        Task task = new Task();
        task.setProjectId(createDto.getProjectId());
        task.setTitle(createDto.getTitle());
        task.setDescription(createDto.getDescription());
        task.setAssignedTo(createDto.getAssignedTo());
        task.setCreatedBy(userId);
        task.setStatus(TaskStatus.TO_DO); // Default status using enum
        task.setPriority(createDto.getPriority()); // Using enum directly
        task.setTaskType(createDto.getTaskType());
        // Validate parentTaskId by business rule
        if (createDto.getParentTaskId() != null) {
            UUID parentId = createDto.getParentTaskId();
            task.setParentTaskId(parentId);
        }
        task.setStartDate(createDto.getStartDate());
        task.setDueDate(createDto.getDueDate());
        task.setPhaseId(createDto.getPhaseId());
        task.setCreatedBy(userId);
        task.setUpdatedBy(userId);
        
        Task savedTask = taskRepository.save(task);
        
        // Create initial status history
        createStatusHistory(savedTask, TaskStatus.TO_DO, userId, "Task created");
        
        // Upload files if present
        if (files != null && files.length > 0) {
            uploadTaskAttachments(savedTask.getId(), files, userId);
        }
        
        return convertToResponseDto(savedTask);
    }

    // Helper method to upload attachments
    private void uploadTaskAttachments(UUID taskId, MultipartFile[] files, UUID userId) {
        try {
            // Call document service to upload files to public folder
            ResponseEntity<com.iems.projectservice.dto.response.DocumentApiResponseDto<List<SimpleFileResponse>>> response =
                documentServiceFeignClient.uploadFilesToPublic(files);
            
            if (response != null && response.getBody() != null && response.getBody().getData() != null) {
                List<SimpleFileResponse> uploadedFiles = response.getBody().getData();
                
                // Save attachment info to database
                for (SimpleFileResponse file : uploadedFiles) {
                    TaskAttachment attachment = new TaskAttachment();
                    attachment.setTaskId(taskId);
                    attachment.setFileId(file.getId());
                    attachment.setFileName(file.getFileName());
                    attachment.setFileUrl(file.getUrl());
                    attachment.setFileType(file.getType());
                    attachment.setUploadedBy(userId);
                    taskAttachmentRepository.save(attachment);
                }
            }
        } catch (Exception e) {
            // Log error but don't fail task creation
            log.error("Error uploading attachments for task {}: {}", taskId, e.getMessage(), e);
        }
    }

    public void deleteAttachment(UUID taskId, UUID attachmentId) {
        UUID userId = userService.getUserIdFromRequest();
        
        // Verify task exists
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.TASK_NOT_FOUND));
        
        // Check permission (creator or assigned user can delete attachments)
        if (!task.getCreatedBy().equals(userId) && !task.getAssignedTo().equals(userId)) {
            throw new AppException(ProjectErrorCode.PERMISSION_DENIED);
        }
        
        // Find attachment
        TaskAttachment attachment = taskAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));
        
        // Verify attachment belongs to this task
        if (!attachment.getTaskId().equals(taskId)) {
            throw new IllegalArgumentException("Attachment does not belong to this task");
        }
        
        // Delete from database first
        taskAttachmentRepository.delete(attachment);
        
        // Delete file from document service asynchronously
        if (attachment.getFileId() != null && !attachment.getFileId().isEmpty()) {
            try {
                documentServiceFeignClient.deleteFile(attachment.getFileId());
            } catch (Exception e) {
                // Log error but don't fail the operation since DB deletion succeeded
                System.err.println("Error deleting file from document service: " + e.getMessage());
            }
        }
    }

    @Override
    public void deleteTask(UUID taskId) {
        UUID userId = userService.getUserIdFromRequest();

        // Verify task exists
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.TASK_NOT_FOUND));

        // Check permission (creator can delete task)
        if (!task.getCreatedBy().equals(userId)) {
            throw new AppException(ProjectErrorCode.PERMISSION_DENIED);
        }

        // Delete all attachments first
        List<TaskAttachment> attachments = taskAttachmentRepository.findByTaskId(taskId);
        for (TaskAttachment attachment : attachments) {
            if (attachment.getFileId() != null && !attachment.getFileId().isEmpty()) {
                try {
                    documentServiceFeignClient.deleteFile(attachment.getFileId());
                } catch (Exception e) {
                    System.err.println("Error deleting file from document service: " + e.getMessage());
                }
            }
        }
        taskAttachmentRepository.deleteAll(attachments);

        // Delete all comments
        List<TaskComment> comments = taskCommentRepository.findByTaskIdOrderByCreatedAtAsc(taskId);
        taskCommentRepository.deleteAll(comments);

        // Delete all status history
        List<TaskStatusHistory> histories = statusHistoryRepository.findByTaskIdOrderByUpdatedAtDesc(taskId);
        statusHistoryRepository.deleteAll(histories);

        // Update subtasks to remove parent reference
        List<Task> subtasks = taskRepository.findByParentTaskId(taskId);
        for (Task subtask : subtasks) {
            subtask.setParentTaskId(null);
            taskRepository.save(subtask);
        }

        // Delete the task
        taskRepository.delete(task);
    }

    // UC24: Gán Nhiệm vụ
    @Override
    public TaskResponseDto assignTask(UUID taskId, UUID newAssigneeId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.TASK_NOT_FOUND));
        UUID userId = userService.getUserIdFromRequest();
        // Check if user has permission to reassign (project manager or task creator)
        if (!task.getCreatedBy().equals(userId)) {
            // TODO: Add project role check here
            throw new AppException(ProjectErrorCode.PERMISSION_DENIED);
        }
        
        UUID oldAssignee = task.getAssignedTo();
        task.setAssignedTo(newAssigneeId);
        task.setUpdatedBy(userId);
        
        Task savedTask = taskRepository.save(task);
        
        // Create status history for reassignment
        String comment = String.format("Task reassigned from %s to %s", oldAssignee, newAssigneeId);
        createStatusHistory(savedTask, savedTask.getStatus(), userId, comment);
        
        return convertToResponseDto(savedTask);
    }

    // UC25: Xem Danh sách Nhiệm vụ Được giao
    @Override
    public List<MyTaskResponseDto> getMyTasks() {
        UUID userId = userService.getUserIdFromRequest();

        // Lấy danh sách task của user
        List<Task> tasks = taskRepository.findByAssignedTo(userId);

        // 1. Lấy tất cả projectId không trùng
        Set<UUID> projectIds = tasks.stream()
                .map(Task::getProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        ProjectIdsDto projectIdsDto = new ProjectIdsDto();
        projectIdsDto.setIds(projectIds);
        System.out.println(projectIdsDto);
        // 2. Gọi project-service lấy projectName 1 lần
        List<ProjectInfoResponse> projectNameList = projectService.getProjectsByID(projectIdsDto);

        // 3. Map projectId → projectName
        Map<UUID, String> projectNameMap = projectNameList.stream()
                .collect(Collectors.toMap(ProjectInfoResponse::getId, ProjectInfoResponse::getName));

        // 4. Map task → DTO
        return tasks.stream()
                .map(task -> convertToMyTaskResponse(task, projectNameMap))
                .collect(Collectors.toList());
    }

    private MyTaskResponseDto convertToMyTaskResponse(Task task, Map<UUID, String> projectNameMap) {
        MyTaskResponseDto dto = new MyTaskResponseDto();

        dto.setId(task.getId());
        dto.setProjectId(task.getProjectId());
        dto.setProjectName(projectNameMap.get(task.getProjectId()));

        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());

        dto.setStatus(task.getStatus() != null ? task.getStatus().name() : null);
        dto.setPriority(task.getPriority() != null ? task.getPriority().name() : null);
        dto.setTaskType(task.getTaskType() != null ? task.getTaskType().name() : null);

        dto.setParentTaskId(task.getParentTaskId());
        dto.setPhaseId(task.getPhaseId());
        dto.setStartDate(task.getStartDate());
        dto.setDueDate(task.getDueDate());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());
        
        // Add attachments
        List<TaskAttachment> attachments = taskAttachmentRepository.findByTaskId(task.getId());
        dto.setAttachments(attachments.stream()
                .map(att -> TaskAttachmentDto.builder()
                        .id(att.getId())
                        .fileName(att.getFileName())
                        .fileUrl(att.getFileUrl())
                        .fileType(att.getFileType())
                        .uploadedAt(att.getUploadedAt())
                        .uploadedBy(att.getUploadedBy())
                        .build())
                .collect(Collectors.toList()));

        return dto;
    }


    // UC26: Cập nhật Trạng thái Nhiệm vụ
    @Override
    public TaskResponseDto updateTaskStatus(UUID taskId, String newStatusStr, String comment) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.TASK_NOT_FOUND));
        UUID userId = userService.getUserIdFromRequest();
        // Check if user is assigned to this task or has permission
        if (!task.getAssignedTo().equals(userId)) {
            // TODO: Add project role check here
            throw new AppException(ProjectErrorCode.PERMISSION_DENIED);
        }
        
        // Convert string to enum
        TaskStatus newStatus;
        try {
            newStatus = TaskStatus.valueOf(newStatusStr);
        } catch (IllegalArgumentException e) {
            throw new AppException(ProjectErrorCode.INVALID_REQUEST);
        }
        
        // Validate status transition
        if (!isValidStatusTransition(task.getStatus(), newStatus)) {
            throw new AppException(ProjectErrorCode.INVALID_REQUEST);
        }
        
        TaskStatus oldStatus = task.getStatus();
        task.setStatus(newStatus);
        task.setUpdatedBy(userId);
        
        Task savedTask = taskRepository.save(task);
        
        // Create status history
        createStatusHistoryWithOldNew(savedTask.getId(), oldStatus, newStatus, userId);
        
        return convertToResponseDto(savedTask);
    }

    @Override
    public List<TaskBulkUpdateItemDto> bulkUpdateStatus(List<UUID> taskIds, String newStatusStr) {
        TaskStatus newStatus;
        try {
            newStatus = TaskStatus.valueOf(newStatusStr);
        } catch (IllegalArgumentException e) {
            throw new AppException(ProjectErrorCode.INVALID_REQUEST);
        }
        UUID userId = userService.getUserIdFromRequest();
        List<TaskBulkUpdateItemDto> updated = new ArrayList<>();
        for (UUID id : taskIds) {
            Task task = taskRepository.findById(id).orElse(null);
            if (task == null) continue;
            // Permissions: creator or assignee
            if (!task.getCreatedBy().equals(userId) && !task.getAssignedTo().equals(userId)) {
                continue;
            }
            TaskStatus oldStatus = task.getStatus();
            if (!isValidStatusTransition(oldStatus, newStatus)) {
                continue;
            }
            task.setStatus(newStatus);
            task.setUpdatedBy(userId);
            Task saved = taskRepository.save(task);
            createStatusHistoryWithOldNew(saved.getId(), oldStatus, newStatus, userId);
            TaskBulkUpdateItemDto item = new TaskBulkUpdateItemDto();
            item.setOldStatus(oldStatus.getDisplayName());
            item.setNewStatus(newStatus.getDisplayName());
            item.setTask(convertToResponseDto(saved));
            updated.add(item);
        }
        return updated;
    }

    @Override
    public TaskCommentDto addComment(UUID taskId, String content, UUID parentCommentId) {
        if (content == null || content.isBlank()) {
            throw new AppException(ProjectErrorCode.INVALID_REQUEST);
        }
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.TASK_NOT_FOUND));
        UUID userId = userService.getUserIdFromRequest();
        TaskComment c = new TaskComment();
        c.setTaskId(task.getId());
        c.setAuthorId(userId);
        c.setContent(content);
        c.setParentCommentId(parentCommentId);
        TaskComment saved = taskCommentRepository.save(c);
        return convertToCommentDto(saved);
    }

    @Override
    public List<TaskCommentDto> getComments(UUID taskId) {
        List<TaskComment> comments = taskCommentRepository.findByTaskIdOrderByCreatedAtAsc(taskId);
        return comments.stream().map(this::convertToCommentDto).toList();
    }

    @Override
    public TaskCommentDto updateComment(UUID commentId, String content) {
        if (content == null || content.isBlank()) {
            throw new AppException(ProjectErrorCode.INVALID_REQUEST);
        }
        TaskComment comment = taskCommentRepository.findById(commentId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.TASK_NOT_FOUND));
        UUID userId = userService.getUserIdFromRequest();
        if (!comment.getAuthorId().equals(userId)) {
            throw new AppException(ProjectErrorCode.PERMISSION_DENIED);
        }
        comment.setContent(content);
        TaskComment saved = taskCommentRepository.save(comment);
        return convertToCommentDto(saved);
    }

    @Override
    public void deleteComment(UUID commentId) {
        TaskComment comment = taskCommentRepository.findById(commentId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.TASK_NOT_FOUND));
        UUID userId = userService.getUserIdFromRequest();
        if (!comment.getAuthorId().equals(userId)) {
            throw new AppException(ProjectErrorCode.PERMISSION_DENIED);
        }
        taskCommentRepository.delete(comment);
    }

    private TaskCommentDto convertToCommentDto(TaskComment comment) {
        String authorName = "";
        String authorAvatar = "";
        try {
            Optional<UserDetailDto> userOpt = userService.getUserById(comment.getAuthorId());
            if (userOpt.isPresent()) {
                UserDetailDto user = userOpt.get();
                String firstName = user.getFirstName() != null ? user.getFirstName() : "";
                String lastName = user.getLastName() != null ? user.getLastName() : "";
                authorName = (firstName + " " + lastName).trim();
                if (authorName.isEmpty()) {
                    authorName = user.getEmail() != null ? user.getEmail() : "Unknown";
                }
                authorAvatar = user.getImage();
            }
        } catch (Exception e) {
            // Log error but don't fail the request
            System.err.println("Failed to fetch user info for comment author: " + comment.getAuthorId() + " - " + e.getMessage());
        }
        // Get parent comment author name if this is a reply
        String parentAuthorName = null;
        if (comment.getParentCommentId() != null) {
            try {
                Optional<TaskComment> parentOpt = taskCommentRepository.findById(comment.getParentCommentId());
                if (parentOpt.isPresent()) {
                    TaskComment parent = parentOpt.get();
                    Optional<UserDetailDto> parentUserOpt = userService.getUserById(parent.getAuthorId());
                    if (parentUserOpt.isPresent()) {
                        UserDetailDto parentUser = parentUserOpt.get();
                        String pFirstName = parentUser.getFirstName() != null ? parentUser.getFirstName() : "";
                        String pLastName = parentUser.getLastName() != null ? parentUser.getLastName() : "";
                        parentAuthorName = (pFirstName + " " + pLastName).trim();
                        if (parentAuthorName.isEmpty()) {
                            parentAuthorName = parentUser.getEmail();
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        
        return TaskCommentDto.builder()
                .id(comment.getId())
                .taskId(comment.getTaskId())
                .authorId(comment.getAuthorId())
                .authorName(authorName.isEmpty() ? "Unknown" : authorName)
                .authorAvatar(authorAvatar)
                .content(comment.getContent())
                .parentCommentId(comment.getParentCommentId())
                .parentAuthorName(parentAuthorName)
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }

    // UC27: Thiết lập Ngày và Mức ưu tiên Nhiệm vụ
    @Override
    public TaskResponseDto updateTaskPriorityAndDates(UUID taskId, UpdateTaskDto updateDto) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.TASK_NOT_FOUND));
        UUID userId = userService.getUserIdFromRequest();
        // Check permissions
        if (!task.getCreatedBy().equals(userId) && !task.getAssignedTo().equals(userId)) {
            // TODO: Add project role check here
            throw new AppException(ProjectErrorCode.PERMISSION_DENIED);
        }
        
        // Validate dates
        if (updateDto.getStartDate() != null && updateDto.getDueDate() != null) {
            if (updateDto.getStartDate().isAfter(updateDto.getDueDate())) {
                throw new AppException(ProjectErrorCode.INVALID_REQUEST);
            }
        }
        
        if (updateDto.getDueDate() != null && updateDto.getDueDate().isBefore(LocalDate.now())) {
            throw new AppException(ProjectErrorCode.INVALID_REQUEST);
        }
        
        boolean hasChanges = false;
        
        if (updateDto.getPriority() != null && !updateDto.getPriority().equals(task.getPriority())) {
            task.setPriority(updateDto.getPriority());
            hasChanges = true;
        }
        
        if (updateDto.getStartDate() != null && !updateDto.getStartDate().equals(task.getStartDate())) {
            task.setStartDate(updateDto.getStartDate());
            hasChanges = true;
        }
        
        if (updateDto.getDueDate() != null && !updateDto.getDueDate().equals(task.getDueDate())) {
            task.setDueDate(updateDto.getDueDate());
            hasChanges = true;
        }
        
        if (hasChanges) {
            task.setUpdatedBy(userId);
            Task savedTask = taskRepository.save(task);
            
            // Create status history for the change
            String changeComment = "Task priority and dates updated";
            if (updateDto.getComment() != null) {
                changeComment += ": " + updateDto.getComment();
            }
            createStatusHistory(savedTask, savedTask.getStatus(), userId, changeComment);
            
            return convertToResponseDto(savedTask);
        }
        
        return convertToResponseDto(task);
    }

    // Get task by ID
    @Override
    public Optional<TaskResponseDto> getTaskById(UUID id) {
        return taskRepository.findById(id).map(this::convertToResponseDto);
    }

    @Override
    public Optional<TaskNestedResponseDto> getTaskByIdNested(UUID id) {
        return taskRepository.findById(id).map(this::convertToNestedResponse);
    }

    // Get all tasks
    @Override
    public List<TaskResponseDto> getAllTasks() {
        List<Task> tasks = taskRepository.findAll();
        Map<UUID, UserDetailDto> userMap = getUserMapFromTasks(tasks);
        return tasks.stream()
                .map(task -> convertToResponseDto(task, userMap))
                .collect(Collectors.toList());
    }

    // Get tasks by project
    @Override
    public List<TaskResponseDto> getTasksByProject(UUID projectId) {
        List<Task> tasks = taskRepository.findByProjectId(projectId);
        Map<UUID, UserDetailDto> userMap = getUserMapFromTasks(tasks);
        return tasks.stream()
                .map(task -> convertToResponseDto(task, userMap))
                .collect(Collectors.toList());
    }

    // Get tasks by project (nested response)
    @Override
    public List<TaskNestedResponseDto> getTasksByProjectNested(UUID projectId) {
        List<Task> tasks = taskRepository.findByProjectId(projectId);
        Map<UUID, UserDetailDto> userMap = getUserMapFromTasks(tasks);
        
        // Get project info ONCE instead of calling FeignClient for each task
        String projectName = null;
        try {
            ProjectDetailResponseDto projectDetail = projectService.getProjectById(projectId);
            if (projectDetail != null) {
                projectName = projectDetail.getName();
            }
        } catch (Exception ignored) {}
        
        final String finalProjectName = projectName;
        return tasks.stream()
                .map(task -> convertToNestedResponse(task, userMap, finalProjectName))
                .collect(Collectors.toList());
    }
    @Override
    public List<TaskResponseDto> getSubtasks(UUID parentTaskId) {
        List<Task> tasks = taskRepository.findByParentTaskId(parentTaskId);
        Map<UUID, UserDetailDto> userMap = getUserMapFromTasks(tasks);
        return tasks.stream()
                .map(task -> convertToResponseDto(task, userMap))
                .collect(Collectors.toList());
    }

    // Generic update: title/description/assignedTo/priority/dates/status
    @Override
    public TaskUpdateResultDto updateTask(UUID taskId, UpdateTaskDto updateDto) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.TASK_NOT_FOUND));
        UUID userId = userService.getUserIdFromRequest();
        // Permissions: creator or current assignee can update; extend with project roles as needed
        if (!task.getCreatedBy().equals(userId) && !task.getAssignedTo().equals(userId)) {
            throw new AppException(ProjectErrorCode.PERMISSION_DENIED);
        }

        boolean hasChanges = false;
        TaskStatus oldStatus = task.getStatus();

        if (updateDto.getTitle() != null && !updateDto.getTitle().isBlank() && !updateDto.getTitle().equals(task.getTitle())) {
            task.setTitle(updateDto.getTitle());
            hasChanges = true;
        }

        if (updateDto.getDescription() != null && !updateDto.getDescription().equals(task.getDescription())) {
            task.setDescription(updateDto.getDescription());
            hasChanges = true;
        }

        if (updateDto.getAssignedTo() != null && !updateDto.getAssignedTo().equals(task.getAssignedTo())) {
            task.setAssignedTo(updateDto.getAssignedTo());
            hasChanges = true;
        }

        if (updateDto.getPriority() != null && !updateDto.getPriority().equals(task.getPriority())) {
            task.setPriority(updateDto.getPriority());
            hasChanges = true;
        }

        if (updateDto.getStartDate() != null && !updateDto.getStartDate().equals(task.getStartDate())) {
            task.setStartDate(updateDto.getStartDate());
            hasChanges = true;
        }

        if (updateDto.getDueDate() != null && !updateDto.getDueDate().equals(task.getDueDate())) {
            task.setDueDate(updateDto.getDueDate());
            hasChanges = true;
        }

        if (updateDto.getStatus() != null && !updateDto.getStatus().equals(task.getStatus())) {
            // validate transition
            if (!isValidStatusTransition(task.getStatus(), updateDto.getStatus())) {
                throw new AppException(ProjectErrorCode.INVALID_REQUEST);
            }
            task.setStatus(updateDto.getStatus());
            hasChanges = true;
        }

        TaskUpdateResultDto result = new TaskUpdateResultDto();
        if (updateDto.getTaskType() != null && !updateDto.getTaskType().equals(task.getTaskType())) {
            task.setTaskType(updateDto.getTaskType());
            hasChanges = true;
        }

        if (updateDto.getParentTaskId() != null && !updateDto.getParentTaskId().equals(task.getParentTaskId())) {
            // Validate parent exists
            UUID parentId = updateDto.getParentTaskId();
            taskRepository.findById(parentId).orElseThrow(() -> new AppException(ProjectErrorCode.TASK_NOT_FOUND));
            task.setParentTaskId(parentId);
            hasChanges = true;
        }

        if (updateDto.getPhaseId() != null && !updateDto.getPhaseId().equals(task.getPhaseId())) {
            task.setPhaseId(updateDto.getPhaseId());
            hasChanges = true;
        }

        if (hasChanges) {
            task.setUpdatedBy(userId);
            Task saved = taskRepository.save(task);
            // Only write history if status actually changed
            if (updateDto.getStatus() != null && !oldStatus.equals(saved.getStatus())) {
                createStatusHistoryWithOldNew(saved.getId(), oldStatus, saved.getStatus(), userId);
                result.setOldStatus(oldStatus.getDisplayName());
                result.setNewStatus(saved.getStatus().getDisplayName());
            }
            result.setTask(convertToNestedResponse(saved));
            return result;
        }
        result.setTask(convertToNestedResponse(task));
        result.setOldStatus(null);
        result.setNewStatus(null);
        return result;
    }

    // Generic update with attachments: title/description/assignedTo/priority/dates/status
    public TaskUpdateResultDto updateTask(UUID taskId, UpdateTaskDto updateDto, MultipartFile[] files) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.TASK_NOT_FOUND));
        UUID userId = userService.getUserIdFromRequest();
        // Permissions: creator or current assignee can update; extend with project roles as needed
        if (!task.getCreatedBy().equals(userId) && !task.getAssignedTo().equals(userId)) {
            throw new AppException(ProjectErrorCode.PERMISSION_DENIED);
        }

        boolean hasChanges = false;
        TaskStatus oldStatus = task.getStatus();

        if (updateDto.getTitle() != null && !updateDto.getTitle().isBlank() && !updateDto.getTitle().equals(task.getTitle())) {
            task.setTitle(updateDto.getTitle());
            hasChanges = true;
        }

        if (updateDto.getDescription() != null && !updateDto.getDescription().equals(task.getDescription())) {
            task.setDescription(updateDto.getDescription());
            hasChanges = true;
        }

        if (updateDto.getAssignedTo() != null && !updateDto.getAssignedTo().equals(task.getAssignedTo())) {
            task.setAssignedTo(updateDto.getAssignedTo());
            hasChanges = true;
        }

        if (updateDto.getPriority() != null && !updateDto.getPriority().equals(task.getPriority())) {
            task.setPriority(updateDto.getPriority());
            hasChanges = true;
        }

        if (updateDto.getStartDate() != null && !updateDto.getStartDate().equals(task.getStartDate())) {
            task.setStartDate(updateDto.getStartDate());
            hasChanges = true;
        }

        if (updateDto.getDueDate() != null && !updateDto.getDueDate().equals(task.getDueDate())) {
            task.setDueDate(updateDto.getDueDate());
            hasChanges = true;
        }

        if (updateDto.getStatus() != null && !updateDto.getStatus().equals(task.getStatus())) {
            // validate transition
            if (!isValidStatusTransition(task.getStatus(), updateDto.getStatus())) {
                throw new AppException(ProjectErrorCode.INVALID_REQUEST);
            }
            task.setStatus(updateDto.getStatus());
            hasChanges = true;
        }

        TaskUpdateResultDto result = new TaskUpdateResultDto();
        if (updateDto.getTaskType() != null && !updateDto.getTaskType().equals(task.getTaskType())) {
            task.setTaskType(updateDto.getTaskType());
            hasChanges = true;
        }

        if (updateDto.getParentTaskId() != null && !updateDto.getParentTaskId().equals(task.getParentTaskId())) {
            // Validate parent exists
            UUID parentId = updateDto.getParentTaskId();
            taskRepository.findById(parentId).orElseThrow(() -> new AppException(ProjectErrorCode.TASK_NOT_FOUND));
            task.setParentTaskId(parentId);
            hasChanges = true;
        }

        if (updateDto.getPhaseId() != null && !updateDto.getPhaseId().equals(task.getPhaseId())) {
            task.setPhaseId(updateDto.getPhaseId());
            hasChanges = true;
        }

        if (hasChanges) {
            task.setUpdatedBy(userId);
            Task saved = taskRepository.save(task);
            // Only write history if status actually changed
            if (updateDto.getStatus() != null && !oldStatus.equals(saved.getStatus())) {
                createStatusHistoryWithOldNew(saved.getId(), oldStatus, saved.getStatus(), userId);
                result.setOldStatus(oldStatus.getDisplayName());
                result.setNewStatus(saved.getStatus().getDisplayName());
            }
            
            // Upload new files if present
            if (files != null && files.length > 0) {
                uploadTaskAttachments(saved.getId(), files, userId);
            }
            
            result.setTask(convertToNestedResponse(saved));
            return result;
        }
        
        // Even if no task changes, still upload files if present
        if (files != null && files.length > 0) {
            uploadTaskAttachments(taskId, files, userId);
        }
        
        result.setTask(convertToNestedResponse(task));
        result.setOldStatus(null);
        result.setNewStatus(null);
        return result;
    }

    // Get active tasks by project (excluding completed)
    @Override
    public List<TaskResponseDto> getActiveTasksByProject(UUID projectId) {
        List<Task> tasks = taskRepository.findActiveTasksByProject(projectId, TaskStatus.COMPLETED);
        Map<UUID, UserDetailDto> userMap = getUserMapFromTasks(tasks);
        return tasks.stream()
                .map(task -> convertToResponseDto(task, userMap))
                .collect(Collectors.toList());
    }

    // Get task status history
    @Override
    public List<TaskStatusHistory> getTaskStatusHistory(UUID taskId) {
        return statusHistoryRepository.findByTaskIdOrderByUpdatedAtDesc(taskId);
    }

    // Helper methods
    @Override
    public void createStatusHistory(Task task, TaskStatus newStatus, UUID updatedBy, String _comment) {
        createStatusHistoryWithOldNew(task.getId(), task.getStatus(), newStatus, updatedBy);
    }

    @Override
    public void createStatusHistoryWithOldNew(UUID taskId, TaskStatus oldStatus, TaskStatus newStatus, UUID updatedBy) {
        TaskStatusHistory history = new TaskStatusHistory();
        history.setTaskId(taskId);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setUpdatedBy(updatedBy);
        statusHistoryRepository.save(history);
    }

    @Override
    public boolean isValidStatusTransition(TaskStatus currentStatus, TaskStatus newStatus) {
        // Define valid status transitions per requirement:
        // TO_DO -> IN_PROGRESS, TO_DO -> COMPLETED, IN_PROGRESS -> COMPLETED, COMPLETED -> IN_PROGRESS
        if (TaskStatus.TO_DO.equals(currentStatus)) {
            return TaskStatus.IN_PROGRESS.equals(newStatus) || TaskStatus.COMPLETED.equals(newStatus);
        } else if (TaskStatus.IN_PROGRESS.equals(currentStatus)) {
            return TaskStatus.COMPLETED.equals(newStatus);
        } else if (TaskStatus.COMPLETED.equals(currentStatus)) {
            return TaskStatus.IN_PROGRESS.equals(newStatus);
        }
        return false;
    }

    // Helper method to get user map from tasks
    private Map<UUID, UserDetailDto> getUserMapFromTasks(List<Task> tasks) {
        Set<UUID> userIds = tasks.stream()
                .flatMap(task -> Stream.of(
                        task.getAssignedTo(),
                        task.getCreatedBy(),
                        task.getUpdatedBy()
                ))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (userIds.isEmpty()) {
            return new HashMap<>();
        }

        UserIdsDto userIdsDto = new UserIdsDto();
        userIdsDto.setIds(userIds);
        List<UserDetailDto> users = userService.getUsersByIds(userIdsDto);
        return users.stream()
                .collect(Collectors.toMap(UserDetailDto::getId, user -> user));
    }

    private TaskResponseDto convertToResponseDto(Task task) {
        TaskResponseDto dto = new TaskResponseDto();
        dto.setId(task.getId());
        dto.setProjectId(task.getProjectId());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setStartDate(task.getStartDate());
        dto.setDueDate(task.getDueDate());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());
        dto.setStatus(task.getStatus().getDisplayName());
        dto.setPriority(task.getPriority().getDisplayName());
        dto.setTaskType(task.getTaskType() != null ? task.getTaskType().getDisplayName() : null);
        dto.setParentTaskId(task.getParentTaskId());
        dto.setPhaseId(task.getPhaseId());

        // AssignedTo
        dto.setAssignedTo(task.getAssignedTo());
        userService.getUserById(task.getAssignedTo()).ifPresent(user -> {
            dto.setAssignedToName(user.getFirstName() + " " + user.getLastName());
            dto.setAssignedToEmail(user.getEmail());
            dto.setAssignedToImage(user.getImage());
        });

        // CreatedBy
        dto.setCreatedBy(task.getCreatedBy());
        userService.getUserById(task.getCreatedBy()).ifPresent(user -> {
            dto.setCreatedByName(user.getFirstName() + " " + user.getLastName());
            dto.setCreatedByEmail(user.getEmail());
            dto.setCreatedByImage(user.getImage());
        });

        // UpdatedBy
        dto.setUpdatedBy(task.getUpdatedBy());
        userService.getUserById(task.getUpdatedBy()).ifPresent(user -> {
            dto.setUpdatedByName(user.getFirstName() + " " + user.getLastName());
            dto.setUpdatedByEmail(user.getEmail());
            dto.setUpdatedByImage(user.getImage());
        });

        // Project name via PROJECT-SERVICE
        try {
            ProjectDetailResponseDto projectDetail = projectService.getProjectById(task.getProjectId());
            if (projectDetail != null && projectDetail.getName() != null) {
                dto.setProjectName(projectDetail.getName());
            }
        } catch (Exception ignored) {
        }

        // Attachments
        List<TaskAttachment> attachments = taskAttachmentRepository.findByTaskId(task.getId());
        dto.setAttachments(attachments.stream()
                .map(att -> TaskAttachmentDto.builder()
                        .id(att.getId())
                        .fileName(att.getFileName())
                        .fileUrl(att.getFileUrl())
                        .fileType(att.getFileType())
                        .uploadedAt(att.getUploadedAt())
                        .uploadedBy(att.getUploadedBy())
                        .build())
                .collect(Collectors.toList()));

        return dto;
    }

    // Overloaded version with user map for batch processing
    private TaskResponseDto convertToResponseDto(Task task, Map<UUID, UserDetailDto> userMap) {
        TaskResponseDto dto = new TaskResponseDto();
        dto.setId(task.getId());
        dto.setProjectId(task.getProjectId());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setStartDate(task.getStartDate());
        dto.setDueDate(task.getDueDate());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());
        dto.setStatus(task.getStatus().getDisplayName());
        dto.setPriority(task.getPriority().getDisplayName());
        dto.setTaskType(task.getTaskType() != null ? task.getTaskType().getDisplayName() : null);
        dto.setParentTaskId(task.getParentTaskId());
        dto.setPhaseId(task.getPhaseId());

        // AssignedTo
        dto.setAssignedTo(task.getAssignedTo());
        UserDetailDto assignedUser = userMap.get(task.getAssignedTo());
        if (assignedUser != null) {
            dto.setAssignedToName(assignedUser.getFirstName() + " " + assignedUser.getLastName());
            dto.setAssignedToEmail(assignedUser.getEmail());
            dto.setAssignedToImage(assignedUser.getImage());
        }

        // CreatedBy
        dto.setCreatedBy(task.getCreatedBy());
        UserDetailDto createdUser = userMap.get(task.getCreatedBy());
        if (createdUser != null) {
            dto.setCreatedByName(createdUser.getFirstName() + " " + createdUser.getLastName());
            dto.setCreatedByEmail(createdUser.getEmail());
            dto.setCreatedByImage(createdUser.getImage());
        }

        // UpdatedBy
        dto.setUpdatedBy(task.getUpdatedBy());
        UserDetailDto updatedUser = userMap.get(task.getUpdatedBy());
        if (updatedUser != null) {
            dto.setUpdatedByName(updatedUser.getFirstName() + " " + updatedUser.getLastName());
            dto.setUpdatedByEmail(updatedUser.getEmail());
            dto.setUpdatedByImage(updatedUser.getImage());
        }

        // Project name via PROJECT-SERVICE
        try {
            ProjectDetailResponseDto res = projectService.getProjectById(task.getProjectId());
            if (res != null && res.getName() != null) {
                @SuppressWarnings("unchecked")
                Object name = res.getName();
                if (name != null) {
                    dto.setProjectName(name.toString());
                }
            }
        } catch (Exception ignored) {
        }

        // Attachments
        List<TaskAttachment> attachments = taskAttachmentRepository.findByTaskId(task.getId());
        dto.setAttachments(attachments.stream()
                .map(att -> TaskAttachmentDto.builder()
                        .id(att.getId())
                        .fileName(att.getFileName())
                        .fileUrl(att.getFileUrl())
                        .fileType(att.getFileType())
                        .uploadedAt(att.getUploadedAt())
                        .uploadedBy(att.getUploadedBy())
                        .build())
                .collect(Collectors.toList()));

        return dto;
    }

    private TaskNestedResponseDto convertToNestedResponse(Task task) {
        TaskNestedResponseDto dto = new TaskNestedResponseDto();
        dto.setId(task.getId());
        TaskNestedResponseDto.ProjectInfo project = new TaskNestedResponseDto.ProjectInfo();
        project.setId(task.getProjectId());
        try {
            ProjectDetailResponseDto projectDetail = projectService.getProjectById(task.getProjectId());
            if (projectDetail != null) {
                project.setName(projectDetail.getName());
            }
        } catch (Exception ignored) {}
        dto.setProject(project);

        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setStatus(task.getStatus().getDisplayName());
        dto.setPriority(task.getPriority().getDisplayName());
        dto.setTaskType(task.getTaskType() != null ? task.getTaskType().getDisplayName() : null);
        dto.setParentTaskId(task.getParentTaskId());
        dto.setPhaseId(task.getPhaseId());
        dto.setStartDate(task.getStartDate());
        dto.setDueDate(task.getDueDate());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());

        TaskNestedResponseDto.UserInfo assigned = new TaskNestedResponseDto.UserInfo();
        assigned.setId(task.getAssignedTo());
        userService.getUserById(task.getAssignedTo()).ifPresent(u -> {
            assigned.setName(u.getFirstName() + " " + u.getLastName());
            assigned.setImage(u.getImage());
        });
        dto.setAssignedTo(assigned);

        TaskNestedResponseDto.UserInfo created = new TaskNestedResponseDto.UserInfo();
        created.setId(task.getCreatedBy());
        userService.getUserById(task.getCreatedBy()).ifPresent(u -> {
            created.setName(u.getFirstName() + " " + u.getLastName());
            created.setImage(u.getImage());
        });
        dto.setCreatedBy(created);

        TaskNestedResponseDto.UserInfo updated = new TaskNestedResponseDto.UserInfo();
        updated.setId(task.getUpdatedBy());
        userService.getUserById(task.getUpdatedBy()).ifPresent(u -> {
            updated.setName(u.getFirstName() + " " + u.getLastName());
            updated.setImage(u.getImage());
        });
        dto.setUpdatedBy(updated);

        // Attachments
        List<TaskAttachment> attachments = taskAttachmentRepository.findByTaskId(task.getId());
        dto.setAttachments(attachments.stream()
                .map(att -> TaskAttachmentDto.builder()
                        .id(att.getId())
                        .fileName(att.getFileName())
                        .fileUrl(att.getFileUrl())
                        .fileType(att.getFileType())
                        .uploadedAt(att.getUploadedAt())
                        .uploadedBy(att.getUploadedBy())
                        .build())
                .collect(Collectors.toList()));

        return dto;
    }

    // Overloaded version with user map for batch processing
    private TaskNestedResponseDto convertToNestedResponse(Task task, Map<UUID, UserDetailDto> userMap) {
        return convertToNestedResponse(task, userMap, null);
    }
    
    // Overloaded version with user map and project name for batch processing (optimized)
    private TaskNestedResponseDto convertToNestedResponse(Task task, Map<UUID, UserDetailDto> userMap, String projectName) {
        TaskNestedResponseDto dto = new TaskNestedResponseDto();
        dto.setId(task.getId());
        TaskNestedResponseDto.ProjectInfo project = new TaskNestedResponseDto.ProjectInfo();
        project.setId(task.getProjectId());
        // Use provided projectName if available, otherwise fallback to ProjectService call
        if (projectName != null) {
            project.setName(projectName);
        } else {
            try {
                ProjectDetailResponseDto projectDetail = projectService.getProjectById(task.getProjectId());
                if (projectDetail != null) {
                    project.setName(projectDetail.getName());
                }
            } catch (Exception ignored) {}
        }
        dto.setProject(project);

        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setStatus(task.getStatus().getDisplayName());
        dto.setPriority(task.getPriority().getDisplayName());
        dto.setTaskType(task.getTaskType() != null ? task.getTaskType().getDisplayName() : null);
        dto.setParentTaskId(task.getParentTaskId());
        dto.setPhaseId(task.getPhaseId());
        dto.setStartDate(task.getStartDate());
        dto.setDueDate(task.getDueDate());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());

        TaskNestedResponseDto.UserInfo assigned = new TaskNestedResponseDto.UserInfo();
        assigned.setId(task.getAssignedTo());
        UserDetailDto assignedUser = userMap.get(task.getAssignedTo());
        if (assignedUser != null) {
            assigned.setName(assignedUser.getFirstName() + " " + assignedUser.getLastName());
            assigned.setImage(assignedUser.getImage());
        }
        dto.setAssignedTo(assigned);

        TaskNestedResponseDto.UserInfo created = new TaskNestedResponseDto.UserInfo();
        created.setId(task.getCreatedBy());
        UserDetailDto createdUser = userMap.get(task.getCreatedBy());
        if (createdUser != null) {
            created.setName(createdUser.getFirstName() + " " + createdUser.getLastName());
            created.setImage(createdUser.getImage());
        }
        dto.setCreatedBy(created);

        TaskNestedResponseDto.UserInfo updated = new TaskNestedResponseDto.UserInfo();
        updated.setId(task.getUpdatedBy());
        UserDetailDto updatedUser = userMap.get(task.getUpdatedBy());
        if (updatedUser != null) {
            updated.setName(updatedUser.getFirstName() + " " + updatedUser.getLastName());
            updated.setImage(updatedUser.getImage());
        }
        dto.setUpdatedBy(updated);

        // Attachments
        List<TaskAttachment> attachments = taskAttachmentRepository.findByTaskId(task.getId());
        dto.setAttachments(attachments.stream()
                .map(att -> TaskAttachmentDto.builder()
                        .id(att.getId())
                        .fileName(att.getFileName())
                        .fileUrl(att.getFileUrl())
                        .fileType(att.getFileType())
                        .uploadedAt(att.getUploadedAt())
                        .uploadedBy(att.getUploadedBy())
                        .build())
                .collect(Collectors.toList()));

        return dto;
    }



    @Override
    public List<ProjectProgressDto> getProjectsProgress(List<UUID> projectIds) {
        List<Task> tasks = taskRepository.findByProjectIdIn(projectIds);

        Map<UUID, List<Task>> tasksByProject = tasks.stream()
                .collect(Collectors.groupingBy(Task::getProjectId));

        List<ProjectProgressDto> result = new ArrayList<>();

        for (UUID projectId : projectIds) {
            List<Task> projectTasks = tasksByProject.getOrDefault(projectId, List.of());

            // Lọc chỉ task có phase
            List<Task> tasksWithPhase = projectTasks.stream()
                    .filter(t -> t.getPhaseId() != null)
                    .collect(Collectors.toList());

            // Group tasks theo phase
            Map<UUID, List<Task>> tasksByPhase = tasksWithPhase.stream()
                    .collect(Collectors.groupingBy(Task::getPhaseId));

            List<PhaseProgressDto> phaseProgressList = new ArrayList<>();

            for (Map.Entry<UUID, List<Task>> entry : tasksByPhase.entrySet()) {
                UUID phaseId = entry.getKey();
                List<Task> phaseTasks = entry.getValue();

                long total = phaseTasks.size();
                long done = phaseTasks.stream()
                        .filter(t -> TaskStatus.COMPLETED.equals(t.getStatus()))
                        .count();

                double phaseProgress = total == 0 ? 0 : ((double) done / total) * 100;

                phaseProgressList.add(new PhaseProgressDto(phaseId, phaseProgress));
            }

            result.add(new ProjectProgressDto(projectId, phaseProgressList));
        }

        return result;
    }
}
