package com.iems.taskservice.service;

import com.iems.taskservice.dto.CreateTaskDto;
import com.iems.taskservice.dto.TaskResponseDto;
import com.iems.taskservice.dto.UpdateTaskDto;
import com.iems.taskservice.entity.Task;
import com.iems.taskservice.entity.TaskStatusHistory;
import com.iems.taskservice.entity.enums.TaskPriority;
import com.iems.taskservice.entity.enums.TaskStatus;
import com.iems.taskservice.repository.TaskRepository;
import com.iems.taskservice.repository.TaskStatusHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class TaskService {
    @Autowired
    private TaskRepository taskRepository;
    
    @Autowired
    private TaskStatusHistoryRepository statusHistoryRepository;

    // UC23: Tạo Nhiệm vụ
    public TaskResponseDto createTask(CreateTaskDto createDto, UUID userId) {
        // Validate dates
        if (createDto.getStartDate() != null && createDto.getDueDate() != null) {
            if (createDto.getStartDate().isAfter(createDto.getDueDate())) {
                throw new IllegalArgumentException("Start date cannot be after due date");
            }
        }
        
        if (createDto.getDueDate() != null && createDto.getDueDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Due date cannot be in the past");
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
    public TaskResponseDto assignTask(UUID taskId, UUID newAssigneeId, UUID userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        
        // Check if user has permission to reassign (project manager or task creator)
        if (!task.getCreatedBy().equals(userId)) {
            // TODO: Add project role check here
            throw new IllegalArgumentException("Insufficient permissions to reassign task");
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
    public List<TaskResponseDto> getMyTasks(UUID userId) {
        List<Task> tasks = taskRepository.findByAssignedTo(userId);
        return tasks.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    // UC26: Cập nhật Trạng thái Nhiệm vụ
    public TaskResponseDto updateTaskStatus(UUID taskId, String newStatusStr, String comment, UUID userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        
        // Check if user is assigned to this task or has permission
        if (!task.getAssignedTo().equals(userId)) {
            // TODO: Add project role check here
            throw new IllegalArgumentException("Insufficient permissions to update task status");
        }
        
        // Convert string to enum
        TaskStatus newStatus;
        try {
            newStatus = TaskStatus.valueOf(newStatusStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + newStatusStr);
        }
        
        // Validate status transition
        if (!isValidStatusTransition(task.getStatus(), newStatus)) {
            throw new IllegalArgumentException("Invalid status transition from " + task.getStatus().getDisplayName() + " to " + newStatus.getDisplayName());
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
    public TaskResponseDto updateTaskPriorityAndDates(UUID taskId, UpdateTaskDto updateDto, UUID userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        
        // Check permissions
        if (!task.getCreatedBy().equals(userId) && !task.getAssignedTo().equals(userId)) {
            // TODO: Add project role check here
            throw new IllegalArgumentException("Insufficient permissions to update task");
        }
        
        // Validate dates
        if (updateDto.getStartDate() != null && updateDto.getDueDate() != null) {
            if (updateDto.getStartDate().isAfter(updateDto.getDueDate())) {
                throw new IllegalArgumentException("Start date cannot be after due date");
            }
        }
        
        if (updateDto.getDueDate() != null && updateDto.getDueDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Due date cannot be in the past");
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
        dto.setAssignedTo(task.getAssignedTo());
        dto.setCreatedBy(task.getCreatedBy());
        dto.setStatus(task.getStatus().getDisplayName()); // Convert enum to display name
        dto.setPriority(task.getPriority().getDisplayName()); // Convert enum to display name
        dto.setStartDate(task.getStartDate());
        dto.setDueDate(task.getDueDate());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());
        dto.setUpdatedBy(task.getUpdatedBy());
        
        // TODO: Populate names from user service calls
        // dto.setProjectName(projectService.getProjectName(task.getProjectId()));
        // dto.setAssignedToName(userService.getUserName(task.getAssignedTo()));
        // dto.setCreatedByName(userService.getUserName(task.getCreatedBy()));
        // dto.setUpdatedByName(userService.getUserName(task.getUpdatedBy()));
        
        return dto;
    }
}