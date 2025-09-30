package com.iems.taskservice.service;

import com.iems.taskservice.Client.UserServiceFeignClient;
import com.iems.taskservice.dto.CreateTaskDto;
import com.iems.taskservice.dto.TaskResponseDto;
import com.iems.taskservice.dto.UpdateTaskDto;
import com.iems.taskservice.dto.UserDetailDto;
import com.iems.taskservice.entity.Task;
import com.iems.taskservice.entity.TaskStatusHistory;
import com.iems.taskservice.entity.enums.TaskPriority;
import com.iems.taskservice.entity.enums.TaskStatus;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import com.iems.taskservice.exception.AppException;
import com.iems.taskservice.exception.TaskErrorCode;

@Service
@Transactional
public class TaskService {
    @Autowired
    private TaskRepository taskRepository;
    
    @Autowired
    private TaskStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private UserServiceFeignClient userServiceFeignClient;

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
        JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getUserId();
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
        createStatusHistory(savedTask, newStatus, userId, comment);
        
        return convertToResponseDto(savedTask);
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
    private void createStatusHistory(Task task, TaskStatus status, UUID updatedBy, String comment) {
        TaskStatusHistory history = new TaskStatusHistory();
        history.setTaskId(task.getId());
        history.setStatus(status);
        history.setUpdatedBy(updatedBy);
        history.setComment(comment);
        statusHistoryRepository.save(history);
    }

    private boolean isValidStatusTransition(TaskStatus currentStatus, TaskStatus newStatus) {
        // Define valid status transitions
        if (TaskStatus.TO_DO.equals(currentStatus)) {
            return TaskStatus.IN_PROGRESS.equals(newStatus);
        } else if (TaskStatus.IN_PROGRESS.equals(currentStatus)) {
            return TaskStatus.COMPLETED.equals(newStatus) || 
                   TaskStatus.TO_DO.equals(newStatus);
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

        // TODO: Project name, nếu bạn có service
        // dto.setProjectName(projectService.getProjectName(task.getProjectId()));

        return dto;
    }
}