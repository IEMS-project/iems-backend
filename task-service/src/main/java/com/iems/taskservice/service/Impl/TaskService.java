package com.iems.taskservice.service.Impl;

import com.iems.taskservice.Client.ProjectServiceFeignClient;
import com.iems.taskservice.dto.*;
import com.iems.taskservice.entity.Task;
import com.iems.taskservice.entity.TaskStatusHistory;
import com.iems.taskservice.entity.enums.TaskStatus;
import com.iems.taskservice.repository.TaskCommentRepository;
import com.iems.taskservice.repository.TaskRepository;
import com.iems.taskservice.repository.TaskStatusHistoryRepository;
import com.iems.taskservice.service.ITaskService;
import com.iems.taskservice.service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.iems.taskservice.exception.AppException;
import com.iems.taskservice.exception.TaskErrorCode;
import com.iems.taskservice.entity.TaskComment;

@Service
@Transactional
public class TaskService implements ITaskService {
    @Autowired
    private TaskRepository taskRepository;
    
    @Autowired
    private TaskStatusHistoryRepository statusHistoryRepository;
    
    @Autowired
    private ProjectServiceFeignClient projectServiceFeignClient;

    @Autowired
    private TaskCommentRepository taskCommentRepository;
    
    @Autowired
    private IUserService userService;


    // UC23: Tạo Nhiệm vụ
    @Override
    public TaskResponseDto createTask(CreateTaskDto createDto) {
        // Validate dates
        UUID userId = userService.getUserIdFromRequest();
        if (createDto.getStartDate() != null && createDto.getDueDate() != null) {
            if (createDto.getStartDate().isAfter(createDto.getDueDate())) {
                throw new AppException(TaskErrorCode.INVALID_REQUEST);
            }
        }
        
        if (createDto.getDueDate() != null && createDto.getDueDate().isBefore(LocalDate.now())) {
            throw new AppException(TaskErrorCode.INVALID_REQUEST);
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
//                    .orElseThrow(() -> new AppException(TaskErrorCode.TASK_NOT_FOUND));
//            if (TaskType.EPIC.equals(parent.getTaskType()) || TaskType.TASK.equals(parent.getTaskType()) || TaskType.STORY.equals(parent.getTaskType()) || TaskType.BUG.equals(parent.getTaskType())) {
//                // Allow any type to be a parent for now; customize if needed
//            }
            task.setParentTaskId(parentId);
        }
        task.setStartDate(createDto.getStartDate());
        task.setDueDate(createDto.getDueDate());
        task.setCreatedBy(userId);
        task.setUpdatedBy(userId);
        
        Task savedTask = taskRepository.save(task);
        
        // Create initial status history
        createStatusHistory(savedTask, TaskStatus.TO_DO, userId, "Task created");
        
        return convertToResponseDto(savedTask);
    }

    // UC24: Gán Nhiệm vụ
    @Override
    public TaskResponseDto assignTask(UUID taskId, UUID newAssigneeId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(TaskErrorCode.TASK_NOT_FOUND));
        UUID userId = userService.getUserIdFromRequest();
        // Check if user has permission to reassign (project manager or task creator)
        if (!task.getCreatedBy().equals(userId)) {
            // TODO: Add project role check here
            throw new AppException(TaskErrorCode.PERMISSION_DENIED);
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
        ResponseEntity<ApiResponseDto<List<ProjectInfoResponse>>> response = projectServiceFeignClient.getProjectsByID(projectIdsDto);
        List<ProjectInfoResponse> projectNameList = response != null && response.getBody() != null && response.getBody().getData() != null
                ? response.getBody().getData()
                : new ArrayList<>();

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
        dto.setStartDate(task.getStartDate());
        dto.setDueDate(task.getDueDate());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());

        return dto;
    }


    // UC26: Cập nhật Trạng thái Nhiệm vụ
    @Override
    public TaskResponseDto updateTaskStatus(UUID taskId, String newStatusStr, String comment) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(TaskErrorCode.TASK_NOT_FOUND));
        UUID userId = userService.getUserIdFromRequest();
        // Check if user is assigned to this task or has permission
        if (!task.getAssignedTo().equals(userId)) {
            // TODO: Add project role check here
            throw new AppException(TaskErrorCode.PERMISSION_DENIED);
        }
        
        // Convert string to enum
        TaskStatus newStatus;
        try {
            newStatus = TaskStatus.valueOf(newStatusStr);
        } catch (IllegalArgumentException e) {
            throw new AppException(TaskErrorCode.INVALID_REQUEST);
        }
        
        // Validate status transition
        if (!isValidStatusTransition(task.getStatus(), newStatus)) {
            throw new AppException(TaskErrorCode.INVALID_REQUEST);
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
            throw new AppException(TaskErrorCode.INVALID_REQUEST);
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
    public TaskComment addComment(UUID taskId, String content) {
        if (content == null || content.isBlank()) {
            throw new AppException(TaskErrorCode.INVALID_REQUEST);
        }
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(TaskErrorCode.TASK_NOT_FOUND));
        UUID userId = userService.getUserIdFromRequest();
        TaskComment c = new TaskComment();
        c.setTaskId(task.getId());
        c.setAuthorId(userId);
        c.setContent(content);
        return taskCommentRepository.save(c);
    }

    @Override
    public List<TaskComment> getComments(UUID taskId) {
        return taskCommentRepository.findByTaskIdOrderByCreatedAtAsc(taskId);
    }

    // UC27: Thiết lập Ngày và Mức ưu tiên Nhiệm vụ
    @Override
    public TaskResponseDto updateTaskPriorityAndDates(UUID taskId, UpdateTaskDto updateDto) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(TaskErrorCode.TASK_NOT_FOUND));
        UUID userId = userService.getUserIdFromRequest();
        // Check permissions
        if (!task.getCreatedBy().equals(userId) && !task.getAssignedTo().equals(userId)) {
            // TODO: Add project role check here
            throw new AppException(TaskErrorCode.PERMISSION_DENIED);
        }
        
        // Validate dates
        if (updateDto.getStartDate() != null && updateDto.getDueDate() != null) {
            if (updateDto.getStartDate().isAfter(updateDto.getDueDate())) {
                throw new AppException(TaskErrorCode.INVALID_REQUEST);
            }
        }
        
        if (updateDto.getDueDate() != null && updateDto.getDueDate().isBefore(LocalDate.now())) {
            throw new AppException(TaskErrorCode.INVALID_REQUEST);
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
            ResponseEntity<Map<String, Object>> res = projectServiceFeignClient.getProjectById(projectId);
            if (res.getBody() != null && res.getBody().containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> p = (Map<String, Object>) res.getBody().get("data");
                Object name = p.get("name");
                projectName = name != null ? name.toString() : null;
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
                .orElseThrow(() -> new AppException(TaskErrorCode.TASK_NOT_FOUND));
        UUID userId = userService.getUserIdFromRequest();
        // Permissions: creator or current assignee can update; extend with project roles as needed
        if (!task.getCreatedBy().equals(userId) && !task.getAssignedTo().equals(userId)) {
            throw new AppException(TaskErrorCode.PERMISSION_DENIED);
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
                throw new AppException(TaskErrorCode.INVALID_REQUEST);
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
            taskRepository.findById(parentId).orElseThrow(() -> new AppException(TaskErrorCode.TASK_NOT_FOUND));
            task.setParentTaskId(parentId);
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
            ResponseEntity<Map<String, Object>> res = projectServiceFeignClient.getProjectById(task.getProjectId());
            if (res.getBody() != null && res.getBody().containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> project = (Map<String, Object>) res.getBody().get("data");
                Object name = project.get("name");
                if (name != null) {
                    dto.setProjectName(name.toString());
                }
            }
        } catch (Exception ignored) {
        }

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
            ResponseEntity<Map<String, Object>> res = projectServiceFeignClient.getProjectById(task.getProjectId());
            if (res.getBody() != null && res.getBody().containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> project = (Map<String, Object>) res.getBody().get("data");
                Object name = project.get("name");
                if (name != null) {
                    dto.setProjectName(name.toString());
                }
            }
        } catch (Exception ignored) {
        }

        return dto;
    }

    private TaskNestedResponseDto convertToNestedResponse(Task task) {
        TaskNestedResponseDto dto = new TaskNestedResponseDto();
        dto.setId(task.getId());
        TaskNestedResponseDto.ProjectInfo project = new TaskNestedResponseDto.ProjectInfo();
        project.setId(task.getProjectId());
        try {
            ResponseEntity<Map<String, Object>> res = projectServiceFeignClient.getProjectById(task.getProjectId());
            if (res.getBody() != null && res.getBody().containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> p = (Map<String, Object>) res.getBody().get("data");
                Object name = p.get("name");
                project.setName(name != null ? name.toString() : null);
            }
        } catch (Exception ignored) {}
        dto.setProject(project);

        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setStatus(task.getStatus().getDisplayName());
        dto.setPriority(task.getPriority().getDisplayName());
        dto.setTaskType(task.getTaskType() != null ? task.getTaskType().getDisplayName() : null);
        dto.setParentTaskId(task.getParentTaskId());
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
        // Use provided projectName if available, otherwise fallback to FeignClient call
        if (projectName != null) {
            project.setName(projectName);
        } else {
            try {
                ResponseEntity<Map<String, Object>> res = projectServiceFeignClient.getProjectById(task.getProjectId());
                if (res.getBody() != null && res.getBody().containsKey("data")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> p = (Map<String, Object>) res.getBody().get("data");
                    Object name = p.get("name");
                    project.setName(name != null ? name.toString() : null);
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

        return dto;
    }
}