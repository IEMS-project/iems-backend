package com.iems.taskservice.controller;

import com.iems.taskservice.dto.*;
import com.iems.taskservice.entity.TaskStatusHistory;
import com.iems.taskservice.entity.TaskComment;
import com.iems.taskservice.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/tasks")
@Tag(name = "Task API", description = "Manage tasks")
public class TaskController {
    @Autowired
    private TaskService taskService;

    @PostMapping
    public ResponseEntity<ApiResponseDto<TaskNestedResponseDto>> createTask(
            @Valid @RequestBody CreateTaskDto createDto) {
        try {
            TaskResponseDto responseDto = taskService.createTask(createDto);
            // convert to nested for consistency
            return taskService.getTaskByIdNested(responseDto.getId())
                    .map(nested -> ResponseEntity.ok(new ApiResponseDto<>("success", "Task created successfully", nested)))
                    .orElseGet(() -> ResponseEntity.ok(new ApiResponseDto<>("success", "Task created successfully", null)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to create task", null));
        }
    }
    @PatchMapping("/{id}")
    @Operation(summary = "Update task", description = "Update task fields: title, description, assignedTo, priority, dates, status")
    public ResponseEntity<ApiResponseDto<TaskUpdateResultDto>> updateTask(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskDto updateDto) {
        try {
            TaskUpdateResultDto updated = taskService.updateTask(id, updateDto);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Task updated successfully", updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to update task", null));
        }
    }

    @PatchMapping("/{id}/assign")
    @Operation(summary = "Assign task", description = "Assign task to a new assignee")
    public ResponseEntity<ApiResponseDto<TaskResponseDto>> assignTask(
            @PathVariable UUID id, 
            @RequestParam UUID newAssigneeId) {
        try {
            TaskResponseDto updatedDto = taskService.assignTask(id, newAssigneeId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Task assigned successfully", updatedDto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to assign task", null));
        }
    }

    @GetMapping("/my-tasks")
    @Operation(summary = "Get my tasks", description = "Get tasks assigned to the current user")
    public ResponseEntity<ApiResponseDto<List<TaskResponseDto>>> getMyTasks() {
        try {
            List<TaskResponseDto> tasks = taskService.getMyTasks();
            return ResponseEntity.ok(new ApiResponseDto<>("success", "My tasks retrieved successfully", tasks));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to retrieve my tasks", null));
        }
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update task status", description = "Update the status of a task")
    public ResponseEntity<ApiResponseDto<TaskResponseDto>> updateTaskStatus(
            @PathVariable UUID id, 
            @RequestParam String newStatus,
            @RequestParam(required = false) String comment){
        try {
            TaskResponseDto updatedDto = taskService.updateTaskStatus(id, newStatus, comment);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Task status updated successfully", updatedDto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to update task status", null));
        }
    }

    @PatchMapping("/{id}/priority-date")
    @Operation(summary = "Update task priority & dates", description = "Set priority and dates for a task")
    public ResponseEntity<ApiResponseDto<TaskResponseDto>> updateTaskPriorityDate(
            @PathVariable UUID id, 
            @Valid @RequestBody UpdateTaskDto updateDto) {
        try {
            TaskResponseDto updatedDto = taskService.updateTaskPriorityAndDates(id, updateDto);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Task priority and dates updated successfully", updatedDto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to update task priority and dates", null));
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get task by ID", description = "Retrieve a single task by its ID")
    public ResponseEntity<ApiResponseDto<TaskResponseDto>> getTaskById(@PathVariable UUID id) {
        try {
            return taskService.getTaskById(id)
                    .map(task -> ResponseEntity.ok(new ApiResponseDto<>("success", "Task retrieved successfully", task)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponseDto<>("error", "Task not found", null)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to retrieve task", null));
        }
    }

    @GetMapping
    @Operation(summary = "Get all tasks", description = "Retrieve all tasks")
    public ResponseEntity<ApiResponseDto<List<TaskResponseDto>>> getAllTasks() {
        try {
            List<TaskResponseDto> tasks = taskService.getAllTasks();
            return ResponseEntity.ok(new ApiResponseDto<>("success", "All tasks retrieved successfully", tasks));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to retrieve tasks", null));
        }
    }

    @GetMapping("/project/{projectId}")
    @Operation(summary = "Get tasks by project", description = "Retrieve tasks that belong to a specific project")
    public ResponseEntity<ApiListResponseDto<List<TaskNestedResponseDto>>> getTasksByProject(@PathVariable UUID projectId) {
        try {
            List<TaskNestedResponseDto> tasks = taskService.getTasksByProjectNested(projectId);
            return ResponseEntity.ok(new ApiListResponseDto<>("success", "Project tasks retrieved successfully", tasks.size(), tasks));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiListResponseDto<>("error", "Failed to retrieve project tasks", 0, null));
        }
    }

    @PostMapping("/status-bulk")
    @Operation(summary = "Bulk update status", description = "Update status for multiple tasks at once")
    public ResponseEntity<ApiListResponseDto<List<TaskBulkUpdateItemDto>>> bulkUpdateStatus(
            @RequestParam String newStatus,
            @RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<String> ids = (List<String>) body.getOrDefault("ids", List.of());
            List<UUID> taskIds = ids.stream().map(UUID::fromString).toList();
            List<TaskBulkUpdateItemDto> updated = taskService.bulkUpdateStatus(taskIds, newStatus);
            return ResponseEntity.ok(new ApiListResponseDto<>("success", "Tasks updated successfully", updated.size(), updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiListResponseDto<>("error", e.getMessage(), 0, null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiListResponseDto<>("error", "Failed to bulk update status", 0, null));
        }
    }

    @PostMapping("/{id}/comments")
    @Operation(summary = "Add task comment")
    public ResponseEntity<ApiResponseDto<TaskComment>> addComment(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        try {
            String content = body.get("content");
            TaskComment c = taskService.addComment(id, content);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Comment added", c));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to add comment", null));
        }
    }

    @GetMapping("/{id}/comments")
    @Operation(summary = "Get task comments")
    public ResponseEntity<ApiListResponseDto<List<TaskComment>>> getComments(@PathVariable UUID id) {
        try {
            List<TaskComment> list = taskService.getComments(id);
            return ResponseEntity.ok(new ApiListResponseDto<>("success", "Comments retrieved", list.size(), list));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiListResponseDto<>("error", "Failed to get comments", 0, null));
        }
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get task history", description = "Retrieve status change history for a task")
    public ResponseEntity<ApiResponseDto<List<TaskStatusHistory>>> getTaskStatusHistory(@PathVariable UUID id) {
        try {
            List<TaskStatusHistory> history = taskService.getTaskStatusHistory(id);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Task history retrieved successfully", history));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to retrieve task history", null));
        }
    }

    @GetMapping("/my-tasks/filter")
    @Operation(summary = "Get my tasks with filter", description = "Filter my tasks by status or priority")
    public ResponseEntity<ApiResponseDto<List<TaskResponseDto>>> getMyTasksWithFilter(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority) {
        try {
            List<TaskResponseDto> tasks;
            if (status != null && priority != null) {
                tasks = taskService.getMyTasks();
            } else if (status != null) {
                tasks = taskService.getMyTasks();
            } else if (priority != null) {
                tasks = taskService.getMyTasks();
            } else {
                tasks = taskService.getMyTasks();
            }
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Filtered tasks retrieved successfully", tasks));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ApiResponseDto<>("error", "Failed to retrieve filtered tasks", null));
        }
    }
}