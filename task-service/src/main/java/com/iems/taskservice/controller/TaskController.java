package com.iems.taskservice.controller;

import com.iems.taskservice.dto.ApiResponse;
import com.iems.taskservice.dto.CreateTaskDto;
import com.iems.taskservice.dto.TaskResponseDto;
import com.iems.taskservice.dto.UpdateTaskDto;
import com.iems.taskservice.entity.TaskStatusHistory;
import com.iems.taskservice.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tasks")
@CrossOrigin(origins = "*")
public class TaskController {
    @Autowired
    private TaskService taskService;

    // UC23: Tạo Nhiệm vụ
    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponseDto>> createTask(
            @Valid @RequestBody CreateTaskDto createDto, 
            @RequestHeader("X-User-Id") UUID userId) {
        try {
            TaskResponseDto responseDto = taskService.createTask(createDto, userId);
            return ResponseEntity.ok(new ApiResponse<>("success", "Task created successfully", responseDto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponse<>("error", "Failed to create task", null));
        }
    }

    // UC24: Gán Nhiệm vụ
    @PutMapping("/{id}/assign")
    public ResponseEntity<ApiResponse<TaskResponseDto>> assignTask(
            @PathVariable UUID id, 
            @RequestParam UUID newAssigneeId,
            @RequestHeader("X-User-Id") UUID userId) {
        try {
            TaskResponseDto updatedDto = taskService.assignTask(id, newAssigneeId, userId);
            return ResponseEntity.ok(new ApiResponse<>("success", "Task assigned successfully", updatedDto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponse<>("error", "Failed to assign task", null));
        }
    }

    // UC25: Xem Danh sách Nhiệm vụ Được giao
    @GetMapping("/my-tasks")
    public ResponseEntity<ApiResponse<List<TaskResponseDto>>> getMyTasks(@RequestHeader("X-User-Id") UUID userId) {
        try {
            List<TaskResponseDto> tasks = taskService.getMyTasks(userId);
            return ResponseEntity.ok(new ApiResponse<>("success", "My tasks retrieved successfully", tasks));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponse<>("error", "Failed to retrieve my tasks", null));
        }
    }

    // UC26: Cập nhật Trạng thái Nhiệm vụ
    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<TaskResponseDto>> updateTaskStatus(
            @PathVariable UUID id, 
            @RequestParam String newStatus,
            @RequestParam(required = false) String comment,
            @RequestHeader("X-User-Id") UUID userId) {
        try {
            TaskResponseDto updatedDto = taskService.updateTaskStatus(id, newStatus, comment, userId);
            return ResponseEntity.ok(new ApiResponse<>("success", "Task status updated successfully", updatedDto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponse<>("error", "Failed to update task status", null));
        }
    }

    // UC27: Thiết lập Ngày và Mức ưu tiên Nhiệm vụ
    @PutMapping("/{id}/priority-date")
    public ResponseEntity<ApiResponse<TaskResponseDto>> updateTaskPriorityDate(
            @PathVariable UUID id, 
            @Valid @RequestBody UpdateTaskDto updateDto, 
            @RequestHeader("X-User-Id") UUID userId) {
        try {
            TaskResponseDto updatedDto = taskService.updateTaskPriorityAndDates(id, updateDto, userId);
            return ResponseEntity.ok(new ApiResponse<>("success", "Task priority and dates updated successfully", updatedDto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponse<>("error", "Failed to update task priority and dates", null));
        }
    }

    // Get task by ID
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponseDto>> getTaskById(@PathVariable UUID id) {
        try {
            return taskService.getTaskById(id)
                    .map(task -> ResponseEntity.ok(new ApiResponse<>("success", "Task retrieved successfully", task)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>("error", "Task not found", null)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponse<>("error", "Failed to retrieve task", null));
        }
    }

    // Get all tasks
    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskResponseDto>>> getAllTasks() {
        try {
            List<TaskResponseDto> tasks = taskService.getAllTasks();
            return ResponseEntity.ok(new ApiResponse<>("success", "All tasks retrieved successfully", tasks));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponse<>("error", "Failed to retrieve tasks", null));
        }
    }

    // Get tasks by project
    @GetMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<List<TaskResponseDto>>> getTasksByProject(@PathVariable UUID projectId) {
        try {
            List<TaskResponseDto> tasks = taskService.getTasksByProject(projectId);
            return ResponseEntity.ok(new ApiResponse<>("success", "Project tasks retrieved successfully", tasks));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponse<>("error", "Failed to retrieve project tasks", null));
        }
    }

    // Get task status history
    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<List<TaskStatusHistory>>> getTaskStatusHistory(@PathVariable UUID id) {
        try {
            List<TaskStatusHistory> history = taskService.getTaskStatusHistory(id);
            return ResponseEntity.ok(new ApiResponse<>("success", "Task history retrieved successfully", history));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponse<>("error", "Failed to retrieve task history", null));
        }
    }

    // Get my tasks with filters
    @GetMapping("/my-tasks/filter")
    public ResponseEntity<ApiResponse<List<TaskResponseDto>>> getMyTasksWithFilter(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority) {
        try {
            List<TaskResponseDto> tasks;
            if (status != null && priority != null) {
                // TODO: Implement combined filter in service
                tasks = taskService.getMyTasks(userId);
            } else if (status != null) {
                // TODO: Implement status filter in service
                tasks = taskService.getMyTasks(userId);
            } else if (priority != null) {
                // TODO: Implement priority filter in service
                tasks = taskService.getMyTasks(userId);
            } else {
                tasks = taskService.getMyTasks(userId);
            }
            return ResponseEntity.ok(new ApiResponse<>("success", "Filtered tasks retrieved successfully", tasks));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponse<>("error", "Failed to retrieve filtered tasks", null));
        }
    }
}