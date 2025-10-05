package com.iems.taskservice.service;

import com.iems.taskservice.Client.ProjectServiceFeignClient;
import com.iems.taskservice.Client.UserServiceFeignClient;
import com.iems.taskservice.dto.*;
import com.iems.taskservice.entity.Task;
import com.iems.taskservice.entity.TaskStatusHistory;
import com.iems.taskservice.entity.enums.TaskPriority;
import com.iems.taskservice.entity.enums.TaskStatus;
import com.iems.taskservice.repository.TaskCommentRepository;
import com.iems.taskservice.repository.TaskRepository;
import com.iems.taskservice.repository.TaskStatusHistoryRepository;
import com.iems.taskservice.security.JwtUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import com.iems.taskservice.exception.AppException;
import com.iems.taskservice.exception.TaskErrorCode;
import com.iems.taskservice.entity.TaskComment;

@Service
@Transactional
public class TaskService {
    @Autowired
    private TaskRepository taskRepository;
    
    @Autowired
    private TaskStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private UserServiceFeignClient userServiceFeignClient;

    @Autowired
    private ProjectServiceFeignClient projectServiceFeignClient;

    @Autowired
    private TaskCommentRepository taskCommentRepository;

    private Optional<UserDetailDto> getUserById(UUID userId) {
        try {
            ResponseEntity<Map<String, Object>> response = userServiceFeignClient.getUserById(userId);

            if (response.getBody() != null && response.getBody().containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> userData = (Map<String, Object>) response.getBody().get("data");
                return Optional.of(convertToUserDetailDto(userData));
            }

            return Optional.empty();
        } catch (Exception e) {
            // Log error and return empty
            System.err.println("Error fetching user " + userId + " from User Service: " + e.getMessage());
            return Optional.empty();
        }
    }

    private UserDetailDto convertToUserDetailDto(Map<String, Object> userData) {
        UserDetailDto dto = new UserDetailDto();
        dto.setId(UUID.fromString(userData.get("id").toString()));
        dto.setFirstName((String) userData.get("firstName"));
        dto.setLastName((String) userData.get("lastName"));
        dto.setEmail((String) userData.get("email"));
        dto.setAddress((String) userData.get("address"));
        dto.setPhone((String) userData.get("phone"));

        // Handle Date objects - convert to string
        Object dob = userData.get("dob");
        dto.setDob(dob != null ? dob.toString() : null);

        // Handle enum objects - convert to string
        Object gender = userData.get("gender");
        dto.setGender(gender != null ? gender.toString() : null);

        dto.setPersonalID((String) userData.get("personalID"));
        dto.setImage((String) userData.get("image"));
        dto.setBankAccountNumber((String) userData.get("bankAccountNumber"));
        dto.setBankName((String) userData.get("bankName"));

        // Handle enum objects - convert to string
        Object contractType = userData.get("contractType");
        dto.setContractType(contractType != null ? contractType.toString() : null);

        // Handle Date objects - convert to string
        Object startDate = userData.get("startDate");
        dto.setStartDate(startDate != null ? startDate.toString() : null);

        dto.setRole((String) userData.get("role"));
        return dto;
    }
    public UUID getUserIdFromRequest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(TaskErrorCode.PERMISSION_DENIED);
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof JwtUserDetails)) {
            throw new AppException(TaskErrorCode.PERMISSION_DENIED);
        }
        JwtUserDetails userDetails = (JwtUserDetails) principal;
        UUID userId = userDetails.getUserId();
        if (userId == null) {
            throw new AppException(TaskErrorCode.PERMISSION_DENIED);
        }
        return userId;
    }

    // UC23: Tạo Nhiệm vụ
    public TaskResponseDto createTask(CreateTaskDto createDto) {
        // Validate dates
        UUID userId = getUserIdFromRequest();
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
    public TaskResponseDto assignTask(UUID taskId, UUID newAssigneeId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(TaskErrorCode.TASK_NOT_FOUND));
        UUID userId = getUserIdFromRequest();
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
    public List<TaskResponseDto> getMyTasks() {
        UUID userId = getUserIdFromRequest();
        List<Task> tasks = taskRepository.findByAssignedTo(userId);
        return tasks.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    // UC26: Cập nhật Trạng thái Nhiệm vụ
    public TaskResponseDto updateTaskStatus(UUID taskId, String newStatusStr, String comment) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(TaskErrorCode.TASK_NOT_FOUND));
        UUID userId = getUserIdFromRequest();
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

    public List<TaskBulkUpdateItemDto> bulkUpdateStatus(List<UUID> taskIds, String newStatusStr) {
        TaskStatus newStatus;
        try {
            newStatus = TaskStatus.valueOf(newStatusStr);
        } catch (IllegalArgumentException e) {
            throw new AppException(TaskErrorCode.INVALID_REQUEST);
        }
        UUID userId = getUserIdFromRequest();
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

    public TaskComment addComment(UUID taskId, String content) {
        if (content == null || content.isBlank()) {
            throw new AppException(TaskErrorCode.INVALID_REQUEST);
        }
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(TaskErrorCode.TASK_NOT_FOUND));
        UUID userId = getUserIdFromRequest();
        TaskComment c = new TaskComment();
        c.setTaskId(task.getId());
        c.setAuthorId(userId);
        c.setContent(content);
        return taskCommentRepository.save(c);
    }

    public List<TaskComment> getComments(UUID taskId) {
        return taskCommentRepository.findByTaskIdOrderByCreatedAtAsc(taskId);
    }

    // UC27: Thiết lập Ngày và Mức ưu tiên Nhiệm vụ
    public TaskResponseDto updateTaskPriorityAndDates(UUID taskId, UpdateTaskDto updateDto) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(TaskErrorCode.TASK_NOT_FOUND));
        UUID userId = getUserIdFromRequest();
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
    public Optional<TaskResponseDto> getTaskById(UUID id) {
        return taskRepository.findById(id).map(this::convertToResponseDto);
    }

    public Optional<TaskNestedResponseDto> getTaskByIdNested(UUID id) {
        return taskRepository.findById(id).map(this::convertToNestedResponse);
    }

    // Get all tasks
    public List<TaskResponseDto> getAllTasks() {
        return taskRepository.findAll().stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    // Get tasks by project
    public List<TaskResponseDto> getTasksByProject(UUID projectId) {
        List<Task> tasks = taskRepository.findByProjectId(projectId);
        return tasks.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    // Get tasks by project (nested response)
    public List<TaskNestedResponseDto> getTasksByProjectNested(UUID projectId) {
        List<Task> tasks = taskRepository.findByProjectId(projectId);
        return tasks.stream()
                .map(this::convertToNestedResponse)
                .collect(Collectors.toList());
    }

    // Generic update: title/description/assignedTo/priority/dates/status
    public TaskUpdateResultDto updateTask(UUID taskId, UpdateTaskDto updateDto) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(TaskErrorCode.TASK_NOT_FOUND));
        UUID userId = getUserIdFromRequest();
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
    public List<TaskResponseDto> getActiveTasksByProject(UUID projectId) {
        List<Task> tasks = taskRepository.findActiveTasksByProject(projectId, TaskStatus.COMPLETED);
        return tasks.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    // Get task status history
    public List<TaskStatusHistory> getTaskStatusHistory(UUID taskId) {
        return statusHistoryRepository.findByTaskIdOrderByUpdatedAtDesc(taskId);
    }

    // Helper methods
    private void createStatusHistory(Task task, TaskStatus newStatus, UUID updatedBy, String _comment) {
        createStatusHistoryWithOldNew(task.getId(), task.getStatus(), newStatus, updatedBy);
    }

    private void createStatusHistoryWithOldNew(UUID taskId, TaskStatus oldStatus, TaskStatus newStatus, UUID updatedBy) {
        TaskStatusHistory history = new TaskStatusHistory();
        history.setTaskId(taskId);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setUpdatedBy(updatedBy);
        statusHistoryRepository.save(history);
    }

    private boolean isValidStatusTransition(TaskStatus currentStatus, TaskStatus newStatus) {
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

        // AssignedTo
        dto.setAssignedTo(task.getAssignedTo());
        getUserById(task.getAssignedTo()).ifPresent(user -> {
            dto.setAssignedToName(user.getFirstName() + " " + user.getLastName());
            dto.setAssignedToEmail(user.getEmail());
            dto.setAssignedToImage(user.getImage());
        });

        // CreatedBy
        dto.setCreatedBy(task.getCreatedBy());
        getUserById(task.getCreatedBy()).ifPresent(user -> {
            dto.setCreatedByName(user.getFirstName() + " " + user.getLastName());
            dto.setCreatedByEmail(user.getEmail());
            dto.setCreatedByImage(user.getImage());
        });

        // UpdatedBy
        dto.setUpdatedBy(task.getUpdatedBy());
        getUserById(task.getUpdatedBy()).ifPresent(user -> {
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
        dto.setStartDate(task.getStartDate());
        dto.setDueDate(task.getDueDate());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());

        TaskNestedResponseDto.UserInfo assigned = new TaskNestedResponseDto.UserInfo();
        assigned.setId(task.getAssignedTo());
        getUserById(task.getAssignedTo()).ifPresent(u -> {
            assigned.setName(u.getFirstName() + " " + u.getLastName());
            assigned.setImage(u.getImage());
        });
        dto.setAssignedTo(assigned);

        TaskNestedResponseDto.UserInfo created = new TaskNestedResponseDto.UserInfo();
        created.setId(task.getCreatedBy());
        getUserById(task.getCreatedBy()).ifPresent(u -> {
            created.setName(u.getFirstName() + " " + u.getLastName());
            created.setImage(u.getImage());
        });
        dto.setCreatedBy(created);

        TaskNestedResponseDto.UserInfo updated = new TaskNestedResponseDto.UserInfo();
        updated.setId(task.getUpdatedBy());
        getUserById(task.getUpdatedBy()).ifPresent(u -> {
            updated.setName(u.getFirstName() + " " + u.getLastName());
            updated.setImage(u.getImage());
        });
        dto.setUpdatedBy(updated);

        return dto;
    }
}